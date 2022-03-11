package hamburg.kaischmidt.functionalcoredemo;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

import static playground.FuncCoreArchitectureFeature.functionalCoreArchitecture;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses()
public class FuncCoreTest {

    @ArchTest
    public static final ArchRule shouldAlignFunctionalCoreArchitecture =
            functionalCoreArchitecture()
                    .shellDefinedBy("hamburg.kaischmidt.functionalcoredemo.shell..")
                    .coreDefinedBy("hamburg.kaischmidt.functionalcoredemo.core..")
                    .wherePredefinedCatalogIsExcluded()
                    .wherePackage("java.util.concurrent..").isConsideredNonDeterministic()
                    .wherePackage("java.lang.invoke..").isConsideredNonDeterministic()
                    .wherePackage("java.util.stream..").isConsideredStrictlySideEffectFree();
}

