package playground;

import com.tngtech.archunit.lang.extension.ArchUnitExtension;
import com.tngtech.archunit.lang.extension.EvaluatedRule;

import java.util.Properties;

public class FuncCoreArchUnitExtension implements ArchUnitExtension {
    @Override
    public String getUniqueIdentifier() {
        return "FuncCoreArchUnitExtension";
    }

    @Override
    public void configure(Properties properties) {
        // No Properties yet
    }

    @Override
    public void handle(EvaluatedRule evaluatedRule) {
        System.out.println("Handle" + evaluatedRule);
    }
}
