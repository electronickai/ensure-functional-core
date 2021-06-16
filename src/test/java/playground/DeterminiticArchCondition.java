package playground;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DeterminiticArchCondition extends ArchCondition<JavaClass> {

    private final DetDataStore classification;
    private final HashMap<String, JavaCodeUnit> ANALYSE_HELPER;
    private final Set<JavaCodeUnit> INFERFACES = new HashSet<>();

    public DeterminiticArchCondition(HashMap<String, JavaCodeUnit> analyseHelper, DetDataStore datastore, Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = analyseHelper;
        classification = datastore;
    }

    /**
     * Because the checkoperation in ArchUnit is operation on every single element of the AST, we collect
     * the operations here and do the main processing in the @finish operation. Due to
     * performance reasons we do also some prechecks, so that we can classify some simple cases.
     *
     * @param javaMethod      The AST element to analysze
     * @param conditionEvents input ans output with the found issues.
     */
    private void collectAndPreClassify(JavaCodeUnit javaMethod, ConditionEvents conditionEvents) {

        ANALYSE_HELPER.put(javaMethod.getFullName(), javaMethod); // Used to perform sone assertions

        /* ecentially classified by configured classification */
        if (classification.alreadyClassified(javaMethod)) {
            return;
        }

        //TODO

        classification.classifyUnsure(javaMethod);

    }


    private boolean pruefemethodenaufrufe(JavaCodeUnit javaMethod, ConditionEvents conditionEvents, boolean isStrict) {
        Set<JavaMethodCall> callsToCheck = javaMethod.getMethodCallsFromSelf();

        if (callsToCheck.isEmpty()) {
            classification.classifySDET(javaMethod);
            return true;
        }

        for (JavaMethodCall call : callsToCheck) {


            //TODO

        }

        if (isStrict) {
            classification.classifySDET(javaMethod);
        } else {
            classification.classifyDDET(javaMethod);
        }
        return true;
    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if (owner.getFullName().startsWith("core.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        System.out.println(classification.info() + " Anzahl offene Interfaces: " + INFERFACES.size());
        boolean rerun = true;
        while (rerun) {
            Set<JavaCodeUnit> unchecked = classification.getClMethods(DetDataStore.ClassificationEnum.UNCHECKED);
            rerun = !unchecked.isEmpty();
            for (JavaCodeUnit meth : unchecked) {
                collectAndPreClassify(meth, conditionEvents);
            }


            for (JavaCodeUnit meth : classification.getClMethods(DetDataStore.ClassificationEnum.UNSURE)) {
                rerun |= pruefemethodenaufrufe(meth, conditionEvents, true);
            }

            rerun |= checkInterfacxes();
            System.out.println(classification.info() + " Anzahl offene Interfaces: " + INFERFACES.size());

        }

        classification.getClMethods(DetDataStore.ClassificationEnum.UNSURE).forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));

    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaCodeUnit meth) {
        if (owner.getFullName().startsWith("core.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !classification.isKnownNotDET(c.getTarget().resolve()) && !classification.isKnownDDET(c.getTarget().resolve())).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private boolean checkInterfacxes() {
        Set<JavaCodeUnit> toRemove = new HashSet<>();
        for (JavaCodeUnit anInterface : INFERFACES) {
            if (anInterface.getOwner().getAllSubClasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(classification::isKnownSDET))) {
                classification.classifySDET(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubClasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(classification::isKnownAtLeastDDET))) {
                classification.classifyDDET(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubClasses().stream().anyMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).anyMatch(classification::isKnownNotDET))) {
                classification.isKnownNotDET(anInterface);
                toRemove.add(anInterface);

            }
        }
        return INFERFACES.removeAll(toRemove);
    }


    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getConstructors().forEach(javaConstructor -> collectAndPreClassify(javaConstructor, conditionEvents));
        javaClass.getMethods().forEach(javaMethod -> collectAndPreClassify(javaMethod, conditionEvents));


    }

}

