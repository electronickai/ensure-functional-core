package playground.sideeffectfree;

import com.tngtech.archunit.core.domain.*;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SideEffectFreeArchCondition extends ArchCondition<JavaClass> {

    private final SefDataStore dataStore;
    private final HashMap<String, JavaCodeUnit> ANALYSE_HELPER;
    private final Set<JavaCodeUnit> INFERFACES = new HashSet<>();

    public SideEffectFreeArchCondition(HashMap<String, JavaCodeUnit> analyseHelper, SefDataStore datastore, Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = analyseHelper;
        dataStore = datastore;
    }

    /**
     * Because the checkoperation in ArchUnit is operation on every single element of the AST, we collect
     * the operations here and do the main processing in the @finish operation. Due to
     * performance reasons we do also some prechecks, so that we can classify some simple cases.
     *
     * @param javaMethod      The AST element to analysze
     * @param conditionEvents input ans output with the found issues.
     */
    private void collectAndPreClassify(JavaCodeUnit javaMethod, ConditionEvents conditionEvents) {

        ANALYSE_HELPER.put(javaMethod.getFullName(), javaMethod); // Used to perform sone assertions

        /* ecentially classified by configured classification */
        if (dataStore.alreadyClassified(javaMethod)) {
            return;
        }

        /* A operation which has no return parameters can not be SEF, because it is either changeing its parametes (call by reference)
         * or it can not do anything. so it is save to classify as not SEF */
        if (!javaMethod.isConstructor() && javaMethod.getRawReturnType().getFullName().equals("void")) {
            dataStore.classifyNotSEF(javaMethod);
            return;
        }

        /* Natiove Operations can not be analyzed, so handle as NotSEF */
        if (javaMethod.getModifiers().contains(JavaModifier.NATIVE)) {
            dataStore.classifyNotSEF(javaMethod);
            return;
        }

        /* Interfaces need special handling, because all its implementation needs to be SEf, so collect separatly */
        if (javaMethod.getOwner().isInterface() || javaMethod.getModifiers().contains(JavaModifier.ABSTRACT)) {
            INFERFACES.add(javaMethod);
            dataStore.classifyUnsure(javaMethod);
            return;
        }

        // Wenn die Operation kein Constructor ist, darf sie nicht auf Properties schreiben
        boolean strict = true;
        if (!javaMethod.isConstructor()) {

            JavaClass javaClass = javaMethod.getOwner();
            Set<JavaField> setfields = javaMethod.getFieldAccesses().stream()
                    .filter(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))
                    .map(m -> m.getTarget().resolveField().orNull()).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (setfields.isEmpty()) {
                strict = true;
            } else if (setfields.stream().allMatch(a -> a.getAccessesToSelf().stream().allMatch(b -> b.getOrigin().equals(javaMethod)))) {
                strict = false;
            } else {
                logViolation(conditionEvents, javaClass,
                        javaMethod.getFullName() + " is writing to at least one property");
                dataStore.classifyNotSEF(javaMethod);
                return;
            }
        }

        // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

        if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
            if (strict) {
                dataStore.classifySSEF(javaMethod);
            } else {
                dataStore.classifyDSEF(javaMethod);
            }
            return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun SEF
        }

        // jetzt wissen wir, dass andere Methoden aufgerufen werden und die Classifizierung nur noch von den Methodenaufrufen abhängt.

        if (validateMethodCalls(javaMethod, conditionEvents, strict)) {
            return;
        }

        // Wenn wir bisher keine Klassifizirung gefunden haben, dann schaffen wir es noch nicht.

        dataStore.classifyUnsure(javaMethod);
    }

    private boolean validateMethodCalls(JavaCodeUnit codeUnit, ConditionEvents conditionEvents, boolean isStrict) {
        Set<JavaMethodCall> callsToCheck = codeUnit.getMethodCallsFromSelf();

        if (callsToCheck.isEmpty()) {
            dataStore.classifySSEF(codeUnit);
            return true;
        }

        for (JavaMethodCall call : callsToCheck) {
            if (dataStore.isUnsure(call.getTarget().resolve())) {
                return false;
            }
            if (dataStore.isKnownNotSEF(call.getTarget().resolve())) {
                logViolation(conditionEvents, codeUnit.getOwner(), codeUnit.getFullName() + " calls not SEF method ( one of" + call.getTarget() + ")");
                dataStore.classifyNotSEF(codeUnit);
                return true;
            }
            if (dataStore.isKnownDSEF(call.getTarget().resolve())) {
                isStrict = false;
            }
        }

        if (isStrict) {
            dataStore.classifySSEF(codeUnit);
        } else {
            dataStore.classifyDSEF(codeUnit);
        }

        return true;
    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if (owner.getFullName().startsWith("app.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + INFERFACES.size());
        boolean rerun = true;
        while (rerun) {
            Set<JavaCodeUnit> unchecked = dataStore.getClMethods(SideEffectFreeClassification.UNCHECKED);
            rerun = !unchecked.isEmpty();
            for (JavaCodeUnit meth : unchecked) {
                collectAndPreClassify(meth, conditionEvents);
            }

            for (JavaCodeUnit meth : dataStore.getClMethods(SideEffectFreeClassification.UNSURE)) {
                rerun |= validateMethodCalls(meth, conditionEvents, true);
            }

            rerun |= checkInterfaces();
            System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + INFERFACES.size());
        }
        dataStore.getClMethods(SideEffectFreeClassification.UNSURE).forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));
    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaCodeUnit meth) {
        if (owner.getFullName().startsWith("app.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !dataStore.isKnownNotSEF(c.getTarget().resolve()) && !dataStore.isKnownDSEF(c.getTarget().resolve())).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private boolean checkInterfaces() {
        Set<JavaCodeUnit> toRemove = new HashSet<>();
        for (JavaCodeUnit anInterface : INFERFACES) {
            if (anInterface.getOwner().getAllSubclasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(dataStore::isKnownSSEF))) {
                dataStore.classifySSEF(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubclasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(dataStore::isKnownAtLeastDSEF))) {
                dataStore.classifyDSEF(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubclasses().stream().anyMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).anyMatch(dataStore::isKnownNotSEF))) {
                dataStore.isKnownNotSEF(anInterface);
                toRemove.add(anInterface);

            }
        }
        return INFERFACES.removeAll(toRemove);
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getConstructors().forEach(javaConstructor -> collectAndPreClassify(javaConstructor, conditionEvents));
        javaClass.getMethods().forEach(javaMethod -> collectAndPreClassify(javaMethod, conditionEvents));
    }
}

