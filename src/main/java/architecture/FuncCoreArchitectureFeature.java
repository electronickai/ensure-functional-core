package architecture;

import com.tngtech.archunit.PublicAPI;
import com.tngtech.archunit.base.Optional;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.thirdparty.com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.PublicAPI.Usage.ACCESS;
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
     * .wherePackage("java.util.stream..").isConsideredPure()
     * </code></pre>
     * NOTE: The packages like java.util.concurrent and java.lan.invoke (and others) would already be declared as non deterministic in a predefined catalog. This catalog could be used by either omitting wherePredefinedCatalogIsExcluded() or by explicitly declaring wherePredefinedCatalogIsUsed()
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
        ;
        private boolean usePredefinedCatalog = true;
        private Set<String> nonDeterministicPackages = new LinkedHashSet<>();
        private Set<String> purePackages = new LinkedHashSet<>();
        //        private Set<String> nonSideEffectFreePackages = new LinkedHashSet<>(); //TODO: Shall we define that as configurable?
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

        private FunctionalCoreArchitecture addNonDeterministicPackage(String packageIdentifier) {
            nonDeterministicPackages.add(packageIdentifier);
            return this;
        }

        private FunctionalCoreArchitecture addPurePackage(String packageIdentifier) {
            purePackages.add(packageIdentifier);
            return this;
        }

        @Override
        public String getDescription() {
            if (overriddenDescription.isPresent()) {
                return overriddenDescription.get();
            }

            List<String> lines = new ArrayList<>();
            lines.add("Functional Core Architecture with core in " + corePackageIdentifiers + " and shell in " + shellPackageIdentifiers);
            lines.add(usePredefinedCatalog ? " using predifined non deterministic packages" : "using only customized non deterministic packages");
            if (!nonDeterministicPackages.isEmpty()) {
                lines.add("with the following additionally defined non deterministic packages");
                lines.addAll(nonDeterministicPackages);
            }
            return Joiner.on(lineSeparator()).join(lines);
        }

        @Override
        public String toString() {
            return getDescription();
        }

        @Override
        public EvaluationResult evaluate(JavaClasses classes) {
            return new EvaluationResult(this, Priority.MEDIUM);
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
            private final String packageIdentifier; //TODO: Use package syntax / PackageMatcher

            private PackageClassification(String packageIdentifier) {
                this.packageIdentifier = packageIdentifier;
            }

            @PublicAPI(usage = ACCESS)
            public FunctionalCoreArchitecture isConsideredNonDeterministic() {
                return FunctionalCoreArchitecture.this.addNonDeterministicPackage(packageIdentifier);
            }

            @PublicAPI(usage = ACCESS)
            public FunctionalCoreArchitecture isConsideredPure() {
                return FunctionalCoreArchitecture.this.addPurePackage(packageIdentifier);
            }
        }
    }
}
