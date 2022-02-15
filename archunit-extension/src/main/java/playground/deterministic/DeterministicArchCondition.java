package playground.deterministic;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DeterministicArchCondition extends ArchCondition<JavaClass> {

    private final DetDataStore dataStore;
    private final HashMap<String, JavaCodeUnit> ANALYSE_HELPER;
    private final Set<JavaCodeUnit> INTERFACES = new HashSet<>();

    public DeterministicArchCondition(HashMap<String, JavaCodeUnit> analyseHelper, Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = analyseHelper;
        dataStore = new DetDataStore();
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
        if (dataStore.alreadyClassified(javaMethod)) {
            return;
        }

        if (javaMethod.getMethodCallsFromSelf().isEmpty() && javaMethod.getFieldAccesses().isEmpty()) {
            dataStore.classifySDET(javaMethod);
            return;
        }

        if (javaMethod.getRawReturnType().getFullName().equals("void")) {
            dataStore.classifySDET(javaMethod);
            return;
        }

        /* Native Operations can not be analyzed, so handle as NotSEF */
        if (javaMethod.getModifiers().contains(JavaModifier.NATIVE)) {
            dataStore.classifyNotDET(javaMethod);
            return;
        }

        dataStore.classifyUnsure(javaMethod);

    }

    private boolean validateMethodCalls(JavaCodeUnit codeUnit, ConditionEvents conditionEvents, boolean isStrict) {
        Set<JavaMethodCall> callsToCheck = codeUnit.getMethodCallsFromSelf();

        if (callsToCheck.isEmpty()) {
            dataStore.classifySDET(codeUnit);
            return true;
        }

        for (JavaMethodCall call : callsToCheck) {
            if (dataStore.isUnsure(call.getTarget().resolve())) {
                return false;
            }
            if (dataStore.isKnownNotDET(call.getTarget().resolve())) {
                dataStore.classifyNotDET(codeUnit);
                return true;
            }
        }

        if (isStrict) {
            dataStore.classifySDET(codeUnit);
        } else {
            dataStore.classifyDDET(codeUnit);
        }

        return true;
    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if (owner.getFullName().startsWith("app.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + INTERFACES.size());
        boolean rerun = true;
        while (rerun) {
            Set<JavaCodeUnit> unchecked = dataStore.getClMethods(DeterministicClassification.UNCHECKED);
            rerun = !unchecked.isEmpty();
            for (JavaCodeUnit meth : unchecked) {
                collectAndPreClassify(meth, conditionEvents);
            }

            for (JavaCodeUnit meth : dataStore.getClMethods(DeterministicClassification.UNSURE)) {
                rerun |= validateMethodCalls(meth, conditionEvents, true);
            }

            rerun |= checkInterfaces();
            System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + INTERFACES.size());
        }
        dataStore.getClMethods(DeterministicClassification.UNSURE).forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));
    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaCodeUnit meth) {
        if (owner.getFullName().startsWith("app.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !dataStore.isKnownNotDET(c.getTarget().resolve()) && !dataStore.isKnownDDET(c.getTarget().resolve())).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private boolean checkInterfaces() {
        Set<JavaCodeUnit> toRemove = new HashSet<>();
        for (JavaCodeUnit anInterface : INTERFACES) {
            if (anInterface.getOwner().getAllSubclasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(dataStore::isKnownSDET))) {
                dataStore.classifySDET(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubclasses().stream().allMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).allMatch(dataStore::isKnownAtLeastDDET))) {
                dataStore.classifyDDET(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubclasses().stream().anyMatch(cl -> cl.getAllMethods().stream().filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes())).anyMatch(dataStore::isKnownNotDET))) {
                dataStore.isKnownNotDET(anInterface);
                toRemove.add(anInterface);

            }
        }
        return INTERFACES.removeAll(toRemove);
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getConstructors().forEach(javaConstructor -> collectAndPreClassify(javaConstructor, conditionEvents));
        javaClass.getMethods().forEach(javaMethod -> collectAndPreClassify(javaMethod, conditionEvents));
    }

    public DetDataStore getDataStore() {
        return dataStore;
    }
}

