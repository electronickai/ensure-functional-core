package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = { "java.lang..", "java.util..", "sun.util.locale..", "sun.util.calendar..", "java.math..", "java.text..", "java.time.."} , importOptions = ImportOption.DoNotIncludeTests.class)
public class TestPlayground {

    private static final ArchCondition<? super JavaClass> BE_PURE
            = new NoSeiteneffectArchCondition(analyse);

    @ArchTest
    public static final ArchRule TEST_TEST
            = classes()
            .should(BE_PURE)
            .because("so they belong to the functional core");

}
