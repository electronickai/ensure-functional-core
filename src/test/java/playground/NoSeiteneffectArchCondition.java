package playground;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class NoSeiteneffectArchCondition extends ArchCondition<JavaClass> {

    private final DataStore classification = new DataStore();
    private final Set<JavaMethod> ANALYSE_HELPER;
    private final Set<JavaMethod> INFERFACES = new HashSet<>();

    public NoSeiteneffectArchCondition(Set<JavaMethod> analyseHelper, Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = analyseHelper;
    }

    /**
     *
     * Because the checkoperation in ArchUnit is operation on every single element of the AST, we collect
     * the operations here and do the main processing in the @finish operation. Due to
     * performance reasons we do also some prechecks, so that we can classify some simple cases.
     *
     * @param javaMethod The AST element to analysze
     * @param conditionEvents input ans output with the found issues.
     */
    private void collectAndPreClassify(JavaMethod javaMethod, ConditionEvents conditionEvents) {

        /* ecentially classified by configured classification */
        if (classification.alreadyClassified(javaMethod)) {
            return;
        }

        /* A operation which has no return parameters can not be SEF, because it is either changeing its parametes (call by reference)
        * or it can not do anything. so it is save to classify as not SEF */
        if(javaMethod.getRawReturnType().getFullName().equals("void")) {
            classification.classifyNotSEF(javaMethod);
            return;
        }

        /* Interfaces need special handling, because all its implementation needs to be SEf, so collect separatly */
        if(javaMethod.getOwner().isInterface()) {
            INFERFACES.add(javaMethod);
            classification.classifyUnsure(javaMethod);
            return;
        }


        JavaClass javaClass = javaMethod.getOwner();
        if (javaMethod.getFieldAccesses().stream()
                .anyMatch(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))) {
            logViolation(conditionEvents, javaClass,
                    javaMethod.getFullName() + " is writing to at least one property");
            classification.classifyNotSEF(javaMethod);
            return;
        }

        // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

        if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
            classification.classifyDSEF(javaMethod);
            return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun SEF
        }

        Set<JavaMethodCall> callsToCheck = javaMethod.getMethodCallsFromSelf();

        for (JavaMethodCall call : callsToCheck) {
            if (classification.isKnownNotSEF(call.getTarget().resolve())) {
                if (javaMethod.getFieldAccesses().stream().map(JavaAccess::getOrigin).filter(ca -> ca instanceof JavaMethod).allMatch(ca -> classification.isKnownDSEF((JavaMethod) ca))) {
                    classification.isKnownDSEF(javaMethod);
                } else {
                    logViolation(conditionEvents, javaMethod.getOwner(),
                            javaMethod.getFullName() + " calls not SEF method ( one of" + call.getTarget()
                                    + ")");
                    classification.classifyNotSEF(javaMethod);
                }
                break;
            }
            if (!classification.isKnownDSEF(call.getTarget().resolve())) {
                classification.classifyUnsure(javaMethod);
                ANALYSE_HELPER.addAll(call.getTarget().resolve());
                break;
            }

        }
        classification.classifyUnsure(javaMethod);

    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if(owner.getFullName().startsWith("core.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        long lastRoundNumberUnsure = 0;
        System.out.println(classification.info() +  " Anzahl offene Interfaces: " + INFERFACES.size());
        while (lastRoundNumberUnsure != classification.getUnshureMethods().size() + INFERFACES.size()) {
            for (JavaMethod meth : classification.getUnshureMethodsClone()) {
                innerChecks(conditionEvents, meth);
            }

            checkInterfacxes();
            lastRoundNumberUnsure =  classification.getUnshureMethods().size() + INFERFACES.size();
            System.out.println(classification.info() +  " Anzahl offene Interfaces: " + INFERFACES.size());

        }

        classification.getUnshureMethods().forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));

        System.out.println(classification.info() +  " Anzahl offene Interfaces: " + INFERFACES.size());

    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaMethod meth) {
        if(owner.getFullName().startsWith("core.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !classification.isKnownNotSEF(c.getTarget().resolve()) && !classification.isKnownDSEF(c.getTarget().resolve()) ).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private void checkInterfacxes() {
        Set<JavaMethod> toRemove = new HashSet<>();
        for(JavaMethod anInterface : INFERFACES) {
            if(anInterface.getOwner().getAllSubClasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(classification::isKnownDSEF))) {
                classification.classifyDSEF(anInterface);
                toRemove.add(anInterface);
            } else {
                if(anInterface.getOwner().getAllSubClasses().stream().anyMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).anyMatch(classification::isKnownNotSEF))) {
                    classification.isKnownNotSEF(anInterface);
                    toRemove.add(anInterface);
                }
            }
        }
        INFERFACES.removeAll(toRemove);
    }

    private void innerChecks(ConditionEvents conditionEvents,  JavaMethod meth) {
        Set<JavaMethodCall> callsToCheck = meth.getMethodCallsFromSelf();
        for (JavaMethodCall call : callsToCheck) {
            if (classification.isKnownNotSEF(call.getTarget().resolve())) {


                if (meth.getFieldAccesses().stream().map(JavaAccess::getOrigin).filter(ca -> ca instanceof JavaMethod).allMatch(ca -> classification.isKnownDSEF((JavaMethod) ca))) {
                    classification.classifyDSEF(meth);
                } else {
                    logViolation(conditionEvents, meth.getOwner(),
                            meth.getFullName() + " calls not SEF method ( one of" + call.getTarget()
                                    + ")");
                    classification.isKnownNotSEF(meth);
                }
                return;
            }
            if (!classification.isKnownDSEF(call.getTarget().resolve())) {
                return;
            }
        }
        classification.classifySSEF(meth);

    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getMethods().forEach(javaMethod -> collectAndPreClassify(javaMethod, conditionEvents));

    }

}

