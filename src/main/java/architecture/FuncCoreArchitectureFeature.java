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

public class FuncCoreArchitectureFeature {
    private FuncCoreArchitectureFeature() {
    }

    @PublicAPI(usage = ACCESS)
    public static FunctionalCoreArchitecture functionalCoreArchitecture() {
        return new FunctionalCoreArchitecture();
    }

    public static final class FunctionalCoreArchitecture implements ArchRule {
        private final String corePackageIdentifier;
        private final String shellPackageIdentifier;
        private final boolean usePredefinedCatalog;
        private final Set<String> additionalNotDeterministicPackages;
        private final Optional<String> overriddenDescription;

        private FunctionalCoreArchitecture() {
            this("", "", true, new LinkedHashSet<>(), Optional.empty());
        }

        public FunctionalCoreArchitecture(String corePackageIdentifier, String shellPackageIdentifier, boolean usePredefinedCatalog, Set<String> additionalNotDeterministicPackages, Optional<String> overriddenDescription) {
            this.corePackageIdentifier = corePackageIdentifier;
            this.shellPackageIdentifier = shellPackageIdentifier;
            this.usePredefinedCatalog = usePredefinedCatalog;
            this.additionalNotDeterministicPackages = additionalNotDeterministicPackages;
            this.overriddenDescription = overriddenDescription;
        }

        @Override
        public String getDescription() {
            if (overriddenDescription.isPresent()) {
                return overriddenDescription.get();
            }

            List<String> lines = new ArrayList<>();
            lines.add("Functional Core Architecture with core in " + corePackageIdentifier + " and shell in " + shellPackageIdentifier);
            lines.add(usePredefinedCatalog ? " using predifined non deterministic packages" : "using only customized non deterministic packages");
            if (!additionalNotDeterministicPackages.isEmpty()) {
                lines.add("with the following additionally defined non deterministic packages");
                lines.addAll(additionalNotDeterministicPackages);
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
    }
}
