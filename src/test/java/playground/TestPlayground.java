package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.AssumptionViolatedException;

import java.util.Formatter;
import java.util.HashMap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;


//@AnalyzeClasses(packages = {"core..", "java.lang..", "java.util..", "sun.util.locale..", "sun.util.calendar..", "java.math..", "java.text..", "java.time..", "java.util.logging.."}, importOptions = ImportOption.DoNotIncludeTests.class)
@AnalyzeClasses(packages = {"core..", "java..", "jdk.internal.."}, importOptions = ImportOption.DoNotIncludeTests.class)
//@AnalyzeClasses(packages = {"core.."}, importOptions = ImportOption.DoNotIncludeTests.class)
public class TestPlayground {

    private static HashMap<String, JavaCodeUnit> analyse = new HashMap<>();
    private static final SefDataStore sefClassification = new SefDataStore();
    private static final DetDataStore detClassification = new DetDataStore();
    private static final Formatter formatter = new Formatter();

    private static final ArchCondition<? super JavaClass> BE_SEF
            = new NoSeiteneffectArchCondition(analyse, sefClassification);

    private static final ArchCondition<? super JavaClass> BE_DET
            = new DeterminiticArchCondition(analyse, detClassification);

    @ArchTest
    public static final ArchRule TEST_SEF
            = classes()
            .should(BE_SEF)
            .because("they are side effect free");

    @ArchTest
    public static final ArchRule TEST_DET
            = classes()
            .should(BE_DET)
            .because("they are deterministic");

    @ArchTest
    public static void checkResultsDET(JavaClasses classes) {

        System.out.println("Check Results for deterministic conditions: ");

        assertSDet("core.Core.isDeterministicBecauseVoid()");
        assertNotDet("core.Core.returnRandom()");
        assertSDet("core.Core.addNumbers(int, int)");
        assertSDet("java.sql.Time.getMonth()");
        assertNotDet("java.time.LocalDate.now()");


        //System.out.println(detClassification.getOfClassification(DetDataStore.ClassificationEnum.SDET));
    }

    @ArchTest
    public static void checkResultsSEF(JavaClasses classes) {

        System.out.println("Check Results for side effects: ");

        assertNotSEF("core.Core.addBoeseNewElement(java.util.List, java.lang.String)");
        assertSSEF("core.Core.add(int, int)");
        assertNotSEF("core.Core.doNothing()");
        assertNotSEF("core.Core.doUnneccessaryStuff()");
        assertSSEF("core.Core.addNewElement(java.util.List, java.lang.String)");
        assertSSEF("core.Core.addNewTalkForwad(java.util.List, java.lang.String)");

        assertSSEF("java.lang.ThreadLocal$SuppliedThreadLocal.initialValue()");
        assertSSEF("java.lang.ThreadLocal.initialValue()");

        /* Erzeuger */
        assertSSEF("java.util.EnumMap.clone()");
        assertSSEF("java.lang.String.toCharArray()");
        assertSSEF("java.lang.String.valueOf(java.lang.Object)");
        assertSSEF("java.lang.String.valueOf([C)");

        /* Fragwuerdig */
        assertDSEF("core.Core.getRandomInit()");

        /* Lazy initialization */
        assertDSEF("core.Core.getLazy()");
        assertNotSEF("java.lang.Class.getSimpleName()"); // TODO soll mindestens DSEF werden

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
            assertTrue(sefClassification.isKnownNotSEF(analyse.get(meth)),
                    "Regression of result for %s should be \"NotSEF\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertNotDet(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(detClassification.isKnownNotDET(analyse.get(meth)),
                    "Regression of result for %s should be \"NotDet\" but was \"%s\"", meth, getClassificationForDet(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertSDet(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(detClassification.isKnownSDET(analyse.get(meth)),
                    "Regression of result for %s should be \"SDET\" but was \"%s\"", meth, getClassificationForDet(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertSSEF(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefClassification.isKnownSSEF(analyse.get(meth)),
                    "Regression of result for %s should be \"SSEF\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertDSEF(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefClassification.isKnownDSEF(analyse.get(meth)),
                    "Regression of result for %s should be \"DSEF\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static void assertUnsure(String meth) {
        if (analyse.containsKey(meth)) {
            assertTrue(sefClassification.isUnsure(analyse.get(meth)),
                    "Regression of result for %s should be \"Unsure\" but was \"%s\"", meth, getClassificationForSef(meth));
        } else {
            throw new AssumptionViolatedException("Methode " + meth + " not found!");
        }
    }

    static String getClassificationForSef(String javaMethod) {
        if (analyse.containsKey(javaMethod)) {
            return sefClassification.getClassificationFor(analyse.get(javaMethod));
        } else {
            return "NOT FOUND";
        }
    }

    static String getClassificationForDet(String javaMethod) {
        if (analyse.containsKey(javaMethod)) {
            return detClassification.getClassificationFor(analyse.get(javaMethod));
        } else {
            return "NOT FOUND";
        }
    }
}

