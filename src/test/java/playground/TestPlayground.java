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
@AnalyzeClasses(packages = {"core..", "java.."}, importOptions = ImportOption.DoNotIncludeTests.class)
//@AnalyzeClasses(packages = {"core.."}, importOptions = ImportOption.DoNotIncludeTests.class)
public class TestPlayground {

    private static final ArchCondition<? super JavaClass> BE_PURE
            = new NoSeiteneffectArchCondition(analyse, classification);

    @ArchTest
    public static final ArchRule TEST_TEST
            = classes()
            .should(BE_PURE)
            .because("so they belong to the functional core");


    @ArchTest
    public static void checkResults(JavaClasses classes) {


        System.out.println("Check Results: ");

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

        System.out.println(classification.getOfClassification(SEFDataStore.ClassificationEnum.DSEF));

    }

}

