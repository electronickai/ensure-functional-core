package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "core" + "..", importOptions = ImportOption.DoNotIncludeTests.class)
public class TestPlayground {

    private static final ArchCondition<? super JavaClass> BE_PURE
            = new ArchCondition<JavaClass>("are pure functions") {

        private final List<String> KNOWN_UNPURE = Arrays.asList("java.lang.StringBuilder");
        private final List<String> KNOWN_PURE = Arrays.asList("java.lang.String");

        private void checkPureness(JavaMethod javaMethod, JavaClass javaClass, ConditionEvents conditionEvents) {
            if (javaMethod.getFieldAccesses().stream()
                    .anyMatch(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))) {
                conditionEvents.add(SimpleConditionEvent.violated(javaClass,
                        "not write  " + javaMethod.getName() + " to its properties (" + javaMethod.getFullName()
                                + ")"));
            }

            if (javaMethod.getFieldAccesses().stream().anyMatch(fa -> fa.getTarget().getFullName().)

            // Nun sind wir schonmal sicher, dass die Methoden nicht direkt auf ihre Properties schreiben.

            if (javaMethod.getMethodCallsFromSelf().isEmpty()) {
                return; // Wenn keine anderen Methoden aufgerufen werden, ist die Methode nun pure
            }

            List<JavaMethodCall> callsToCheck = javaMethod.getMethodCallsFromSelf().stream()
                    .filter(me -> !me.getTarget().getOwner().equals(javaClass))
                    .filter(me -> !KNOWN_PURE.contains(me.getTargetOwner().getFullName()))
                    .collect(Collectors.toList()); // Calls auf die Klasse selber sollten ok sein

            for (JavaMethodCall call : callsToCheck) {
                if (KNOWN_UNPURE.contains(call.getTargetOwner().getFullName())) {
                    conditionEvents.add(SimpleConditionEvent.violated(javaClass,
                            javaMethod.getName() + " calls unpure classes (" + javaMethod.getFullName()
                                    + ")"));
                } else {
                    call.
                    System.out.println("Unsure with call " + call.getName() + " in " + javaMethod.getName() + " (" + javaMethod.getFullName() + ")");
                }
            }


        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
            javaClass.getMethods().forEach(javaMethod -> checkPureness(javaMethod, javaClass, conditionEvents));

        }
    };

    @ArchTest
    public static final ArchRule TEST_TEST
            = classes()
            .should(BE_PURE)
            .because("so they belong to the functional core");

}
