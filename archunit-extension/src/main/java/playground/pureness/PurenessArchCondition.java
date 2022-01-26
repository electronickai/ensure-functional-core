package playground.pureness;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class PurenessArchCondition extends ArchCondition<JavaClass> {

    private final PureDataStore dataStore;
    private final HashMap<String, JavaCodeUnit> ANALYSE_HELPER;
    private final Set<JavaCodeUnit> INTERFACES = new HashSet<>();

    public PurenessArchCondition(Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = new HashMap<>();
        dataStore = new PureDataStore();
    }

    /**
     * Because the checkoperation in ArchUnit is operation on every single element of the AST, we collect
     * the operations here and do the main processing in the @finish operation. Due to
     * performance reasons we do also some prechecks, so that we can classify some simple cases.
     *
     * @param javaMethod      The AST element to analyze
     * @param conditionEvents input and output with the found issues.
     */
    private void collectAndPreClassify(JavaCodeUnit javaMethod, ConditionEvents conditionEvents) {

        ANALYSE_HELPER.put(javaMethod.getFullName(), javaMethod); // Used to perform some assertions

        /* essentially classified by configured classification */
        if (dataStore.alreadyClassified(javaMethod)) {
            return;
        }

        /* A operation which has no return parameters can not be SEF, because it is either changing its
         * parameters (call by reference) , its local state, or it cannot do anything. so it is safe to classify as
         * not SEF. However, it may be side effect free in case there may be some exception to be thrown as long as
         * it isn't thrown at runtime. As we can't check the runtime behaviour here, we don't classify strictly as
         * notSEF in this case for pragmatic reasons */
        if (!javaMethod.isConstructor() && javaMethod.getRawReturnType().getFullName().equals("void") && !throwsException(javaMethod)) {
            dataStore.classifyNotSEF(javaMethod);
            return;
        }

        /* Native Operations can not be analyzed, so handle as NotSEF */
        if (javaMethod.getModifiers().contains(JavaModifier.NATIVE)) {
            dataStore.classifyNotSEF(javaMethod);
            return;
        }

        /* Interfaces and abstract classes need special handling, because all its implementation needs to be SEf, so collect separately */
        if (javaMethod.getOwner().isInterface() || javaMethod.getModifiers().contains(JavaModifier.ABSTRACT)) {
            INTERFACES.add(javaMethod);
            dataStore.classifyUnsure(javaMethod);
            return;
        }

        // Wenn die Operation kein Constructor ist, darf sie nicht auf Properties schreiben
        PurenessClassification premilaryClassification;
        if (!javaMethod.isConstructor()) {
            premilaryClassification = checkForFieldAccesses(javaMethod, conditionEvents);

            if(premilaryClassification == null) {
                return;
            }
        } else {
            // Construktoren sind sef, da sie das erste Mal setzen
            premilaryClassification = PurenessClassification.SSEF;
        }


        // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

        if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
                dataStore.classifyAs(javaMethod, premilaryClassification);
            return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun SEF
        }

        // jetzt wissen wir, dass andere Methoden aufgerufen werden und die Klassifizierung nur noch von den Methodenaufrufen abhängt.

        if (validateMethodCalls(javaMethod, conditionEvents, premilaryClassification)) {
            return;
        }

        // Wenn wir bisher keine Klassifizierung gefunden haben, dann schaffen wir es noch nicht.

        dataStore.classifyUnsure(javaMethod);
    }

    private boolean throwsException(JavaCodeUnit javaMethod) {
        return javaMethod.getConstructorCallsFromSelf()
                .stream()
                .anyMatch(javaConstructorCall -> javaConstructorCall.getTargetOwner().isAssignableTo(Exception.class));
    }

    /**
     * @param javaMethod
     * @param conditionEvents
     * @return
     */
    private PurenessClassification checkForFieldAccesses(JavaCodeUnit javaMethod, ConditionEvents conditionEvents) {

        Set<JavaField> setfields = javaMethod.getFieldAccesses().stream()
                .filter(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))
                .map(m -> m.getTarget().resolveField().orElse(null)).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (setfields.isEmpty()) {
            return PurenessClassification.SSEF;
        }

        if (setfields.stream().allMatch(a -> a.getAccessesToSelf().stream().allMatch(b -> b.getOrigin().equals(javaMethod)))) {
            return PurenessClassification.DSEF;
        }

         logViolation(conditionEvents, javaMethod.getOwner(),
                    javaMethod.getFullName() + " is writing to at least one property");
        dataStore.classifyNotSEF(javaMethod);
        return null;
    }

    private boolean validateMethodCalls(JavaCodeUnit codeUnit, ConditionEvents conditionEvents, PurenessClassification premilaryClassification ) {
        Set<JavaMethodCall> callsToCheck = codeUnit.getMethodCallsFromSelf();

        if (callsToCheck.isEmpty()) {
            dataStore.classifySSEF(codeUnit);
            return true;
        }

        for (JavaMethodCall call : callsToCheck) {
            if (dataStore.isUnsure(call.getTarget().resolve())) {
                return false;
            }
            if (dataStore.isKnownNotSEF(call.getTarget().resolve()) && isVisibleToOuterScope(call, codeUnit)) {
                logViolation(conditionEvents, codeUnit.getOwner(), codeUnit.getFullName() + " calls not SEF method ( one of" + call.getTarget() + ")");
                dataStore.classifyNotSEF(codeUnit);
                return true;
            }
            if (dataStore.isKnownDSEF(call.getTarget().resolve())) {
                premilaryClassification = PurenessClassification.DSEF;
            }
        }

        dataStore.classifyAs(codeUnit, premilaryClassification);

        return true;
    }

    private boolean isVisibleToOuterScope(JavaMethodCall call, JavaCodeUnit codeUnit) {
        Set<JavaClass> internalInstantiations = codeUnit.getConstructorCallsFromSelf()
                .stream()
                .map(constructorCall -> constructorCall.getTarget().getOwner())
                .collect(Collectors.toUnmodifiableSet());
        return !internalInstantiations.contains(call.getTarget().getOwner());
    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if (owner.getFullName().startsWith("hamburg.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + INTERFACES.size());
        boolean rerun = true;
        while (rerun) {
            Set<JavaCodeUnit> unchecked = dataStore.getAllMethodsOfClassification(PurenessClassification.UNCHECKED);
            rerun = !unchecked.isEmpty();
            for (JavaCodeUnit meth : unchecked) {
                collectAndPreClassify(meth, conditionEvents);
            }

            for (JavaCodeUnit meth : dataStore.getAllMethodsOfClassification(PurenessClassification.UNSURE)) {
                rerun |= validateMethodCalls(meth, conditionEvents, PurenessClassification.SSEF);
            }

            rerun |= checkInterfaces();
            System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + INTERFACES.size());
        }
        dataStore.getAllMethodsOfClassification(PurenessClassification.UNSURE).forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));
    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaCodeUnit meth) {
        if (owner.getFullName().startsWith("app.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !dataStore.isKnownNotSEF(c.getTarget().resolve()) && !dataStore.isKnownDSEF(c.getTarget().resolve())).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private boolean checkInterfaces() {
        Set<JavaCodeUnit> toRemove = new HashSet<>();
        for (JavaCodeUnit anInterface : INTERFACES) {
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
        return INTERFACES.removeAll(toRemove);
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getConstructors().forEach(javaConstructor -> collectAndPreClassify(javaConstructor, conditionEvents));
        javaClass.getMethods().forEach(javaMethod -> collectAndPreClassify(javaMethod, conditionEvents));
    }

    public PureDataStore getDataStore() {
        return dataStore;
    }
}

