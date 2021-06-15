package playground;

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

public class NoSeiteneffectArchCondition extends ArchCondition<JavaClass> {

    private final DataStore classification;
    private final HashMap<String, JavaCodeUnit> ANALYSE_HELPER;
    private final Set<JavaCodeUnit> INFERFACES = new HashSet<>();

    public NoSeiteneffectArchCondition(HashMap<String, JavaCodeUnit> analyseHelper, DataStore datastore, Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = analyseHelper;
        classification = datastore;
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
        if (classification.alreadyClassified(javaMethod)) {
            return;
        }

        /* A operation which has no return parameters can not be SEF, because it is either changeing its parametes (call by reference)
         * or it can not do anything. so it is save to classify as not SEF */
        if (!javaMethod.isConstructor() && javaMethod.getRawReturnType().getFullName().equals("void")) {
            classification.classifyNotSEF(javaMethod);
            return;
        }

        /* Natiove Operations can not be analyzed, so handle as NotSEF */
        if (javaMethod.getModifiers().contains(JavaModifier.NATIVE)) {
            classification.classifyNotSEF(javaMethod);
            return;
        }



        /* Interfaces need special handling, because all its implementation needs to be SEf, so collect separatly */
        if (javaMethod.getOwner().isInterface() || javaMethod.getModifiers().contains(JavaModifier.ABSTRACT)) {
            INFERFACES.add(javaMethod);
            classification.classifyUnsure(javaMethod);
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
                classification.classifyNotSEF(javaMethod);
                return;
            }
        }

        // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

        if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
            if (strict) {
                classification.classifySSEF(javaMethod);
            } else {
                classification.classifyDSEF(javaMethod);
            }
            return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun SEF
        }

        // jetzt wissen wir, dass andere Methoden aufgerufen werden und die Classifizierung nur noch von den Methodenaufrufen abhängt.

        if (pruefemethodenaufrufe(javaMethod, conditionEvents, strict)) {
            return;
        }

        // Wenn wir bisher keine Klassifizirung gefunden haben, dann schaffen wir es noch nicht.

        classification.classifyUnsure(javaMethod);

    }


    private boolean pruefemethodenaufrufe(JavaCodeUnit javaMethod, ConditionEvents conditionEvents, boolean isStrict) {
        Set<JavaMethodCall> callsToCheck = javaMethod.getMethodCallsFromSelf();

        if (callsToCheck.isEmpty()) {
            classification.classifySSEF(javaMethod);
            return true;
        }

        for (JavaMethodCall call : callsToCheck) {

            if (classification.isUnsure(call.getTarget().resolve())) {
                return false;
            }

            if (classification.isKnownNotSEF(call.getTarget().resolve())) {
                logViolation(conditionEvents, javaMethod.getOwner(),
                        javaMethod.getFullName() + " calls not SEF method ( one of" + call.getTarget()
                                + ")");
                classification.classifyNotSEF(javaMethod);
                return true;
            }

            if (classification.isKnownDSEF(call.getTarget().resolve())) {
                isStrict = false;
            }
        }

        if (isStrict) {
            classification.classifySSEF(javaMethod);
        } else {
            classification.classifyDSEF(javaMethod);
        }
        return true;
    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if (owner.getFullName().startsWith("core.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        System.out.println(classification.info() + " Anzahl offene Interfaces: " + INFERFACES.size());
        boolean rerun = true;
        while (rerun) {
            rerun = false;
            for (JavaCodeUnit meth : classification.getUnshureMethodsClone()) {
                rerun |= pruefemethodenaufrufe(meth, conditionEvents, true);
            }

            rerun |= checkInterfacxes();
            System.out.println(classification.info() + " Anzahl offene Interfaces: " + INFERFACES.size());

        }

        classification.getUnshureMethods().forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));

        System.out.println(classification.info() + " Anzahl offene Interfaces: " + INFERFACES.size());

    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaCodeUnit meth) {
        if (owner.getFullName().startsWith("core.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !classification.isKnownNotSEF(c.getTarget().resolve()) && !classification.isKnownDSEF(c.getTarget().resolve())).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private boolean checkInterfacxes() {
        Set<JavaCodeUnit> toRemove = new HashSet<>();
        for (JavaCodeUnit anInterface : INFERFACES) {
            if (anInterface.getOwner().getAllSubClasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(classification::isKnownSSEF))) {
                classification.classifySSEF(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubClasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(classification::isKnownAtLeastDSEF))) {
                classification.classifyDSEF(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubClasses().stream().anyMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).anyMatch(classification::isKnownNotSEF))) {
                classification.isKnownNotSEF(anInterface);
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

