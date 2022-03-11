package playground;

import com.tngtech.archunit.PublicAPI;
import com.tngtech.archunit.base.Optional;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.thirdparty.com.google.common.base.Joiner;
import playground.pureness.PureDataStore;
import playground.pureness.PurenessArchCondition;
import playground.pureness.StandardCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.PublicAPI.Usage.ACCESS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.lang.System.lineSeparator;

public final class FuncCoreArchitectureFeature {
    private FuncCoreArchitectureFeature() {
    }

    /**
     * Can be used to assert a typical functional core architecture, where the core is built based on functional programming paradigms (having only pure methods) and a shell to administer side effects.
     * <br><br>
     * A functional core architecture can for example be defined like this:
     * <pre><code>functionalCoreArchitecture()
     * .shellDefinedBy("my.application.module.shell..")
     * .coreDefinedBy("my.application.module.core..")
     * .wherePredefinedCatalogIsExcluded()
     * .wherePackage("java.util.concurrent..").isConsideredNonDeterministic()
     * .wherePackage("java.lang.invoke..").isConsideredNonDeterministic()
     * .wherePackage("java.util.stream..").isConsideredStrictlySideEffectFree()
     * </code></pre>
     * NOTE: The packages like java.util.concurrent and java.lang.invoke (and others) would already be declared as non-deterministic in a predefined catalog. This catalog could be used by either omitting wherePredefinedCatalogIsExcluded() or by explicitly declaring wherePredefinedCatalogIsUsed()
     *
     * @return An {@link ArchRule} enforcing the specified functional core architecture
     **/
    @PublicAPI(usage = ACCESS)
    public static FunctionalCoreArchitecture functionalCoreArchitecture() {
        return new FunctionalCoreArchitecture();
    }

    public static final class FunctionalCoreArchitecture implements ArchRule {
        private String[] shellPackageIdentifiers = new String[0];
        private String[] corePackageIdentifiers = new String[0];

        private boolean usePredefinedCatalog = true;

        private final Set<String> nonSideEffectFreePackages = new LinkedHashSet<>();
        private final Set<String> domainSpecificSideEffectFreePackages = new LinkedHashSet<>();
        private final Set<String> strictlySideEffectFreePackages = new LinkedHashSet<>();

        private final Optional<String> overriddenDescription = Optional.empty();

        private FunctionalCoreArchitecture() {
        }

        @PublicAPI(usage = ACCESS)
        public FunctionalCoreArchitecture shellDefinedBy(String... packageIdentifiers) {
            shellPackageIdentifiers = packageIdentifiers;
            return this;
        }

        @PublicAPI(usage = ACCESS)
        public FunctionalCoreArchitecture coreDefinedBy(String... packageIdentifiers) {
            corePackageIdentifiers = packageIdentifiers;
            return this;
        }

        @PublicAPI(usage = ACCESS)
        public FunctionalCoreArchitecture wherePredefinedCatalogIsExcluded() {
            usePredefinedCatalog = false;
            return this;
        }

        @PublicAPI(usage = ACCESS)
        public FunctionalCoreArchitecture wherePredefinedCatalogIsUsed() {
            usePredefinedCatalog = true;
            return this;
        }

        @PublicAPI(usage = ACCESS)
        public PackageClassification wherePackage(String packageIdentifier) {
            return new PackageClassification(packageIdentifier);
        }

        private FunctionalCoreArchitecture addNonSideEffectFreePackage(String packageIdentifier) {
            nonSideEffectFreePackages.add(packageIdentifier);
            return this;
        }

        private FunctionalCoreArchitecture addDomainSpecificSideEffectFreePackage(String packageIdentifier) {
            domainSpecificSideEffectFreePackages.add(packageIdentifier);
            return this;
        }

        private FunctionalCoreArchitecture addStrictlySideEffectFreePackage(String packageIdentifier) {
            strictlySideEffectFreePackages.add(packageIdentifier);
            return this;
        }

        @Override
        public String getDescription() {
            if (overriddenDescription.isPresent()) {
                return overriddenDescription.get();
            }

            List<String> lines = new ArrayList<>();
            lines.add("Functional Core Architecture with core in " + Arrays.toString(corePackageIdentifiers) + " and shell in " + Arrays.toString(shellPackageIdentifiers));
            lines.add(usePredefinedCatalog ? " using predefined catalog" : "using only user declared packages");
            if (!strictlySideEffectFreePackages.isEmpty()) {
                lines.add("with the following additionally defined strictly side effect free packages");
                lines.addAll(strictlySideEffectFreePackages);
            }
            if (!domainSpecificSideEffectFreePackages.isEmpty()) {
                lines.add("with the following additionally defined domain specific side effect free packages");
                lines.addAll(domainSpecificSideEffectFreePackages);
            }
            if (!nonSideEffectFreePackages.isEmpty()) {
                lines.add("with the following additionally defined non side effect free packages");
                lines.addAll(nonSideEffectFreePackages);
            }
            return Joiner.on(lineSeparator()).join(lines);
        }

        @Override
        public String toString() {
            return getDescription();
        }

        @Override
        public EvaluationResult evaluate(JavaClasses classes) {
            EvaluationResult result = new EvaluationResult(this, Priority.MEDIUM);
            result.add(classes().that().resideInAnyPackage(corePackageIdentifiers).should().onlyDependOnClassesThat().resideOutsideOfPackages(shellPackageIdentifiers).evaluate(classes));
            PurenessArchCondition condition = new PurenessArchCondition();
            initializeCatalog(condition);
            result.add(classes().that().resideInAnyPackage(corePackageIdentifiers).should(condition).evaluate(classes));
            return result;
        }

        private void initializeCatalog(PurenessArchCondition condition) {
            final PureDataStore dataStore = condition.getDataStore();
            if (usePredefinedCatalog) {
                dataStore.addPrefixesForNotSideEffectFree(StandardCatalog.getNotSefPrefixes());
                dataStore.addPrefixesForDomainSpecificSideEffectFree(StandardCatalog.getDsefPrefixes());
                dataStore.addPrefixesForSideEffectFree(StandardCatalog.getSsefPrefixes());
            }
            dataStore.addPrefixesForNotSideEffectFree(nonSideEffectFreePackages);
            dataStore.addPrefixesForDomainSpecificSideEffectFree(domainSpecificSideEffectFreePackages);
            dataStore.addPrefixesForSideEffectFree(strictlySideEffectFreePackages);
        }

        @Override
        public void check(JavaClasses classes) {
            Assertions.check(this, classes);
        }

        @Override
        public ArchRule because(String reason) {
            return null;
        }

        @Override
        public ArchRule as(String newDescription) {
            return null;
        }

        public final class PackageClassification {
            private final String packageIdentifier;

            private PackageClassification(String packageIdentifier) {
                this.packageIdentifier = packageIdentifier;
            }

            @PublicAPI(usage = ACCESS)
            public FunctionalCoreArchitecture isConsideredNonSideEffectFree() {
                return FunctionalCoreArchitecture.this.addNonSideEffectFreePackage(packageIdentifier);
            }

            @PublicAPI(usage = ACCESS)
            public FunctionalCoreArchitecture isConsideredDomainSpecificSideEffectFree() {
                return FunctionalCoreArchitecture.this.addDomainSpecificSideEffectFreePackage(packageIdentifier);
            }

            @PublicAPI(usage = ACCESS)
            public FunctionalCoreArchitecture isConsideredStrictlySideEffectFree() {
                return FunctionalCoreArchitecture.this.addStrictlySideEffectFreePackage(packageIdentifier);
            }
        }
    }
}
