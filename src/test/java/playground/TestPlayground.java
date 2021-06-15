package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.After;
import org.junit.AssumptionViolatedException;

import java.util.Formatter;
import java.util.HashMap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;


//@AnalyzeClasses(packages = {"core..", "java.lang..", "java.util..", "sun.util.locale..", "sun.util.calendar..", "java.math..", "java.text..", "java.time..", "java.util.logging.."}, importOptions = ImportOption.DoNotIncludeTests.class)
@AnalyzeClasses(packages = {"core..", "java.."}, importOptions = ImportOption.DoNotIncludeTests.class)
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
        assertNotSEF("java.lang.String.toCharArray()"); // TODO soll mindestens DSEF werden
        assertSSEF("java.lang.String.valueOf(java.lang.Object)");
        assertSSEF("java.lang.String.valueOf([C)");

        /* Fragwuerdig */
        assertDSEF("core.Core.getRandomInit()");

        /* Lazy initialization */
        assertDSEF("core.Core.getLazy()");
        assertSSEF("java.lang.Class.getSimpleName()");

        /* Native Operations */
        assertSSEF("java.lang.Object.hashCode()");
        assertNotSEF("java.lang.Thread.isAlive()"); // TODO soll mindestens DSEF werden

        /** Strings */
        //assertSSEF("java.lang.String.chars()");
        System.out.println("As expected");

        //System.out.println(classification.getStringOfUnshure());
        System.out.println(classification.getStringOfDSEF());

    }

}

/*

        assertTrue (getClassificationFor("core.Core.doNothing()").equals("NOT FOUND"), "Regression of core.Core.doNothing()");
        assertTrue (getClassificationFor("core.Core.doUnneccessaryStuff()").equals("NOT FOUND"), "Regression of core.Core.doUnneccessaryStuff()");
 */