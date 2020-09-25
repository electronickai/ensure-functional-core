package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class NoSeiteneffectArchCondition extends ArchCondition<JavaClass> {


    private final Set<JavaMethod> KNOWN_PURE_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_UNPURE_METH = new HashSet<>();
    private final Set<JavaMethod> UNSURE_METH = new HashSet<>();
    private final Set<String> UNPURE_API = Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.",  "org.w3c." );
    private final Set<JavaMethod> ANALYSE_HELPER;

    public NoSeiteneffectArchCondition(Set<JavaMethod> analyseHelper, Object... args) {
        super("should side effect free ", args);
        ANALYSE_HELPER = analyseHelper;
    }



    private boolean isKnownPure(JavaMethod methode) {
        return methode.isConstructor() || KNOWN_PURE_METH.contains(methode);
    }

    private boolean isKnownPure(Collection<JavaMethod> methods) {
        return methods.isEmpty() ||methods.stream().anyMatch(this::isKnownPure);
    }

    private boolean isKnownUnPure(JavaMethod methode) {
        return KNOWN_UNPURE_METH.contains(methode) || UNPURE_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    private boolean isKnownUnPure(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownUnPure);
    }

    private void checkPureness(JavaMethod javaMethod, ConditionEvents conditionEvents) {

        if(isKnownUnPure(javaMethod)) {
            UNSURE_METH.remove(javaMethod);
            return;}

        JavaClass javaClass = javaMethod.getOwner();
        if (javaMethod.getFieldAccesses().stream()
                .anyMatch(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))) {
            conditionEvents.add(SimpleConditionEvent.violated(javaClass,
                    javaMethod.getFullName() + "is writing to at least one property"));
            KNOWN_UNPURE_METH.add(javaMethod);
            return;
        }

        // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

        if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
            KNOWN_PURE_METH.add(javaMethod);
            return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun pure
        }

        Set<JavaMethodCall> callsToCheck = javaMethod.getMethodCallsFromSelf();

        for (JavaMethodCall call : callsToCheck) {
            if (isKnownUnPure(call.getTarget().resolve())) {
                conditionEvents.add(SimpleConditionEvent.violated(javaClass,
                        javaMethod.getFullName() + " calls unpure method ( one of" + call.getTarget()
                                + ")"));
                KNOWN_UNPURE_METH.add(javaMethod);
                break;
            }
            if (!isKnownPure(call.getTarget().resolve())) {
                UNSURE_METH.add(javaMethod);
                ANALYSE_HELPER.addAll(call.getTarget().resolve());
                break;
            }

        }
        KNOWN_PURE_METH.add(javaMethod);

    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        long lastRoundNumberUnsure = 0;
        while (lastRoundNumberUnsure != UNSURE_METH.size()) {
            System.out.println("New Round !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            Set<JavaMethod> new_unsure = new HashSet<>();
            for (JavaMethod meth : UNSURE_METH) {


                innerChecks(conditionEvents, new_unsure, meth);

                KNOWN_PURE_METH.add(meth);
                new_unsure.add(meth);
            }
            lastRoundNumberUnsure = UNSURE_METH.size();
            UNSURE_METH.removeAll(new_unsure);

        }

        System.out.println("Anzahl pure: " + KNOWN_PURE_METH.size() + "  Anzahl unsure: " + UNSURE_METH.size()+ "  Anzahl unpure: " + KNOWN_UNPURE_METH.size());

    }

    private void innerChecks(ConditionEvents conditionEvents, Set<JavaMethod> new_unsure, JavaMethod meth) {
        Set<JavaMethodCall> callsToCheck = meth.getMethodCallsFromSelf();
        for (JavaMethodCall call : callsToCheck) {
            if (isKnownUnPure(call.getTarget().resolve())) {
                conditionEvents.add(SimpleConditionEvent.violated(meth.getOwner(),
                        meth.getFullName() + " calls unpure method ( one of" + call.getTarget()
                                + ")"));
                KNOWN_UNPURE_METH.add(meth);
                new_unsure.add(meth);
                break;
            }
            if (!isKnownPure(call.getTarget().resolve())) {
                System.out.println("Still Unsure with call " + call.getTarget().resolve()   + " in " + meth.getFullName());
                break;
            }

        }
    }

    private void debugout() {

        KNOWN_PURE_METH.stream().map(JavaCodeUnit::getFullName).sorted().forEach(a->System.out.println("Pure " + a));
        UNSURE_METH.stream().map(JavaCodeUnit::getFullName).sorted().forEach(a->System.out.println("Unsure " + a));
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getMethods().forEach(javaMethod -> checkPureness(javaMethod, conditionEvents));
        //debugout();

    }

}

