package playground;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class NoSeiteneffectArchCondition extends ArchCondition<JavaClass> {

    private final Set<JavaMethod> KNOWN_STRICT_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_NOT_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> UNSURE_METH = new HashSet<>();
    private final Set<JavaMethod> INFERFACES = new HashSet<>();
    private final Set<String> NOT_SEF_API = Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream");
    private final Set<JavaMethod> ANALYSE_HELPER;

    public NoSeiteneffectArchCondition(Set<JavaMethod> analyseHelper, Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = analyseHelper;
    }


    private boolean isKnownSEF(JavaMethod methode) {
        return methode.isConstructor() || KNOWN_SEF_METH.contains(methode) || KNOWN_STRICT_SEF_METH.contains(methode);
    }

    private boolean isKnownSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSEF);
    }

    private boolean isKnownNotSEF(JavaMethod methode) {
        return KNOWN_NOT_SEF_METH.contains(methode) || NOT_SEF_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    private boolean isKnownNotSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotSEF);
    }

    private void checkSEF(JavaMethod javaMethod, ConditionEvents conditionEvents) {

        if (isKnownNotSEF(javaMethod)) {
            UNSURE_METH.remove(javaMethod);
            return;
        }

        if(javaMethod.getRawReturnType().getFullName().equals("void")) {
            KNOWN_NOT_SEF_METH.add(javaMethod);
            return;
        }


        if(javaMethod.getOwner().isInterface()) {
            INFERFACES.add(javaMethod);
            return;
        }

        JavaClass javaClass = javaMethod.getOwner();
        if (javaMethod.getFieldAccesses().stream()
                .anyMatch(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))) {
            logViolation(conditionEvents, javaClass,
                    javaMethod.getFullName() + " is writing to at least one property");
            KNOWN_NOT_SEF_METH.add(javaMethod);
            return;
        }

        // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

        if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
            KNOWN_SEF_METH.add(javaMethod);
            return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun SEF
        }

        Set<JavaMethodCall> callsToCheck = javaMethod.getMethodCallsFromSelf();

        for (JavaMethodCall call : callsToCheck) {
            if (isKnownNotSEF(call.getTarget().resolve())) {
                if (javaMethod.getFieldAccesses().stream().map(JavaAccess::getOrigin).filter(ca -> ca instanceof JavaMethod).allMatch(ca -> isKnownSEF((JavaMethod) ca))) {
                    KNOWN_SEF_METH.add(javaMethod);
                } else {
                    logViolation(conditionEvents, javaMethod.getOwner(),
                            javaMethod.getFullName() + " calls not SEF method ( one of" + call.getTarget()
                                    + ")");
                    KNOWN_NOT_SEF_METH.add(javaMethod);
                }
                break;
            }
            if (!isKnownSEF(call.getTarget().resolve())) {
                UNSURE_METH.add(javaMethod);
                ANALYSE_HELPER.addAll(call.getTarget().resolve());
                break;
            }

        }
        KNOWN_STRICT_SEF_METH.add(javaMethod);

    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if(owner.getFullName().startsWith("core.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        long lastRoundNumberUnsure = 0;
        while (lastRoundNumberUnsure != UNSURE_METH.size() + INFERFACES.size()) {
            Set<JavaMethod> not_unsure = new HashSet<>();
            for (JavaMethod meth : UNSURE_METH) {
                innerChecks(conditionEvents, not_unsure, meth);
            }

            lastRoundNumberUnsure =  UNSURE_METH.size() + INFERFACES.size();
            checkInterfacxes();
            UNSURE_METH.removeAll(not_unsure);

        }

        UNSURE_METH.forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));
        System.out.println("Anzahl strict SEF:  " + KNOWN_STRICT_SEF_METH.size() + "  Anzahl SEF: " + KNOWN_SEF_METH.size() + "  Anzahl unsure: " + UNSURE_METH.size() + "  Anzahl not SEF: " + KNOWN_NOT_SEF_METH.size() + " Anzahl offene Interfaces: " + INFERFACES.size());

    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaMethod meth) {
        if(owner.getFullName().startsWith("core.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !isKnownNotSEF(c.getTarget().resolve()) && !isKnownSEF(c.getTarget().resolve()) ).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private void checkInterfacxes() {
        Set<JavaMethod> toRemove = new HashSet<>();
        for(JavaMethod anInterface : INFERFACES) {
            if(anInterface.getOwner().getAllSubClasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(this::isKnownSEF))) {
                KNOWN_SEF_METH.add(anInterface);
                toRemove.add(anInterface);
            } else {
                if(anInterface.getOwner().getAllSubClasses().stream().anyMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).anyMatch(this::isKnownNotSEF))) {
                    KNOWN_NOT_SEF_METH.add(anInterface);
                    toRemove.add(anInterface);
                }
            }
        }
        INFERFACES.removeAll(toRemove);
    }

    private void innerChecks(ConditionEvents conditionEvents, Set<JavaMethod> not_unsure, JavaMethod meth) {
        Set<JavaMethodCall> callsToCheck = meth.getMethodCallsFromSelf();
        for (JavaMethodCall call : callsToCheck) {
            if (isKnownNotSEF(call.getTarget().resolve())) {


                if (meth.getFieldAccesses().stream().map(JavaAccess::getOrigin).filter(ca -> ca instanceof JavaMethod).allMatch(ca -> isKnownSEF((JavaMethod) ca))) {
                    KNOWN_SEF_METH.add(meth);
                } else {
                    logViolation(conditionEvents, meth.getOwner(),
                            meth.getFullName() + " calls not SEF method ( one of" + call.getTarget()
                                    + ")");
                    KNOWN_NOT_SEF_METH.add(meth);
                }
                not_unsure.add(meth);
                return;
            }
            if (!isKnownSEF(call.getTarget().resolve())) {
                return;
            }
        }

        KNOWN_STRICT_SEF_METH.add(meth);
        not_unsure.add(meth);

    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getMethods().forEach(javaMethod -> checkSEF(javaMethod, conditionEvents));

    }

}

