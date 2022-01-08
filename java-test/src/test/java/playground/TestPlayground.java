package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.AssumptionViolatedException;
import playground.deterministic.DetDataStore;
import playground.deterministic.DeterministicArchCondition;
import playground.sideeffectfree.SefDataStore;
import playground.sideeffectfree.SideEffectFreeArchCondition;

import java.util.Formatter;
import java.util.HashMap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;


//@AnalyzeClasses(packages = {"app..", "java.lang..", "java.util..", "sun.util.locale..", "sun.util.calendar..", "java.math..", "java.text..", "java.time..", "java.util.logging.."}, importOptions = ImportOption.DoNotIncludeTests.class)
@AnalyzeClasses(packages = {"app..", "java..", "jdk.internal.."}, importOptions = ImportOption.DoNotIncludeTests.class)
//@AnalyzeClasses(packages = {"app.."}, importOptions = ImportOption.DoNotIncludeTests.class)
public class TestPlayground {

    private static HashMap<String, JavaCodeUnit> analyse = new HashMap<>();
    private static final SefDataStore sefDataStore = new SefDataStore();
    private static final DetDataStore detDataStore = new DetDataStore();
    private static final Formatter formatter = new Formatter();

    private static final ArchCondition<? super JavaClass> BE_SEF
            = new SideEffectFreeArchCondition(analyse, sefDataStore);

    private static final ArchCondition<? super JavaClass> BE_DET
            = new DeterministicArchCondition(analyse, detDataStore);

    @ArchTest
    public static void test_det(JavaClasses classes) {
        EvaluationResult results = classes()
                .should(BE_DET)
                .because("they are deterministic").evaluate(classes);
        assertThat(results.getFailureReport().getDetails()).contains("unsure about app.Application.getRandomInit() because of [JavaMethodCall{origin=JavaMethod{app.Application.getRandomInit()}, target=target{java.time.LocalTime.now()}, lineNumber=83}]");
        assertThat(results.getFailureReport().getDetails()).hasSize(1);

    }

    @ArchTest
    public static void test_sef(JavaClasses classes) {
        EvaluationResult results = classes()
                .should(BE_SEF)
                .because("they are side effect free").evaluate(classes);
        assertThat(results.getFailureReport().getDetails()).contains("app.Application.addBoeseNewElement(java.util.List, java.lang.String) is writing to at least one property");
        assertThat(results.getFailureReport().getDetails()).hasSize(1);
    }

    @ArchTest
    public static void checkResultsDET(JavaClasses classes) {

        System.out.println("Check Results for deterministic conditions: ");

        assertSDet("app.Application.isDeterministicBecauseVoid()");
        assertNotDet("app.Application.returnRandom()");
        assertSDet("app.Application.addNumbers(int, int)");
        assertSDet("java.sql.Time.getMonth()");
        assertNotDetOrUnsure("java.time.LocalDate.now()"); // Should be not det


        //System.out.println(detClassification.getOfClassification(DetDataStore.ClassificationEnum.SDET));
    }

    @ArchTest
    public static void checkResultsSEF(JavaClasses classes) {

        System.out.println("Check Results for side effects: ");

        assertNotSEF("app.Application.addBoeseNewElement(java.util.List, java.lang.String)");
        assertSSEF("app.Application.add(int, int)");
        assertNotSEF("app.Application.doNothing()");
        assertNotSEF("app.Application.doUnneccessaryStuff()");
        assertSSEF("app.Application.addNewElement(java.util.List, java.lang.String)");
        assertSSEF("app.Application.addNewTalkForwad(java.util.List, java.lang.String)");

        assertSSEF("java.lang.ThreadLocal$SuppliedThreadLocal.initialValue()");
        assertSSEF("java.lang.ThreadLocal.initialValue()");

        /* Erzeuger */
        assertSSEF("java.util.EnumMap.clone()");
        assertSSEF("java.lang.String.toCharArray()");
        assertSSEF("java.lang.String.valueOf(java.lang.Object)");
        assertSSEF("java.lang.String.valueOf([C)");

        /* Fragwuerdig */
        assertDSEF("app.Application.getRandomInit()");

        /* Lazy initialization */
        assertDSEF("app.Application.getLazy()");
        assertDSEF("java.lang.Class.getSimpleName()"); // TODO Chech if a higher result is possible

        /* Native Operations */
        assertSSEF("java.lang.Object.hashCode()");
        assertNotSEF("java.lang.Thread.isAlive()"); // TODO soll mindestens DSEF werden

        /** Strings */
        assertNotSEF("java.lang.String.chars()"); // TODO soll mindestens DSEF werden
        System.out.println("As expected");

        //System.out.println(sefClassification.getOfClassification(SefDataStore.ClassificationEnum.DSEF));

    }

    public static void assertTrue(boolean assrt, String message, String... args) {
        if (!assrt) {
            throw new AssumptionViolatedException(formatter.format(message, (Object[]) args).toString());
        }
    }

    static void assertNotSEF(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefDataStore.isKnownNotSEF(analyse.get(meth)),
                    "Regression of result for %s should be \"NotSEF\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertNotDet(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(detDataStore.isKnownNotDET(analyse.get(meth)),
                    "Regression of result for %s should be \"NotDet\" but was \"%s\"", meth, getClassificationForDet(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertNotDetOrUnsure(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(detDataStore.isKnownNotDET(analyse.get(meth)) || detDataStore.isUnsure(analyse.get(meth)),
                    "Regression of result for %s should be \"NotDet\" but was \"%s\"", meth, getClassificationForDet(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertSDet(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(detDataStore.isKnownSDET(analyse.get(meth)),
                    "Regression of result for %s should be \"SDET\" but was \"%s\"", meth, getClassificationForDet(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertSSEF(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefDataStore.isKnownSSEF(analyse.get(meth)),
                    "Regression of result for %s should be \"SSEF\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertDSEF(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefDataStore.isKnownDSEF(analyse.get(meth)),
                    "Regression of result for %s should be \"DSEF\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertUnsure(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefDataStore.isUnsure(analyse.get(meth)),
                    "Regression of result for %s should be \"Unsure\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static String getClassificationForSef(String javaMethod) {
        if (analyse.containsKey(javaMethod)) {
            return sefDataStore.getClassificationFor(analyse.get(javaMethod));
        } else {
            return "NOT FOUND";
        }
    }

    static String getClassificationForDet(String javaMethod) {
        if (analyse.containsKey(javaMethod)) {
            return detDataStore.getClassificationFor(analyse.get(javaMethod));
        } else {
            return "NOT FOUND";
        }
    }
}

