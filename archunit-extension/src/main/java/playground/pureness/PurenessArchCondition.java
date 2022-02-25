package playground.pureness;

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

public class PurenessArchCondition extends ArchCondition<JavaClass> {

    private final PureDataStore dataStore;
    //TODO KSC 20.02.22: Just protocols each checked code unit. Is this field still of use?
    private final HashMap<String, JavaCodeUnit> ANALYSE_HELPER;
    private final Set<JavaCodeUnit> interfaces = new HashSet<>();

    public PurenessArchCondition(Object... args) {
        super("side effect free", args);
        ANALYSE_HELPER = new HashMap<>();
        dataStore = new PureDataStore();
    }

    /**
     * Because the checkoperation in ArchUnit is operation on every single element of the AST, we collect
     * the operations here and do the main processing in the @finish operation. Due to
     * performance reasons we do also some prechecks, so that we can classify some simple cases.
     *
     * @param codeUnit        The AST element to analyze
     * @param conditionEvents input and output of the issues found yet.
     */
    private void collectAndPreClassify(JavaCodeUnit codeUnit, ConditionEvents conditionEvents) {

        ANALYSE_HELPER.put(codeUnit.getFullName(), codeUnit); // Used to perform some assertions

        // either classified by configured classification or method is already called by a checked class
        if (dataStore.alreadyClassified(codeUnit)) {
            return;
        }

        // void methods are not side effect free, because otherwise they wouldn't have any effect and would be useless
        if (checkVoidMethodAsNotSef(codeUnit)) {
            dataStore.classifyNotSEF(codeUnit);
            return;
        }
        //TODO KSC 20.02.22: Couldn't we likewise say that methods without parameters are either just returning a constant or can't be deterministic?
//        if (!codeUnit.isConstructor() && codeUnit.getParameters().isEmpty()) {
//            dataStore.classifyNotSEF(codeUnit);
//        }

        // Native Operations can not be analyzed, so consider them as NotSEF and perhaps classify as SEF by configuration
        if (codeUnit.getModifiers().contains(JavaModifier.NATIVE)) {
            dataStore.classifyNotSEF(codeUnit);
            logViolation(conditionEvents, codeUnit.getOwner(),
                    codeUnit.getFullName() + " is a native method");
            return;
        }

        // Interfaces and abstract classes need special handling, because all its implementation needs to be SEF, so collect separately
        if (codeUnit.getOwner().isInterface() || codeUnit.getModifiers().contains(JavaModifier.ABSTRACT)) {
            interfaces.add(codeUnit);
            dataStore.classifyUnsure(codeUnit);
            return;
        }

        // All next investigations are dependent from
        Set<JavaMethodCall> calledMethods = codeUnit.getMethodCallsFromSelf();

        if (calledMethods.isEmpty()) {
            // constructors without any further method calls are side effect free
            if (codeUnit.isConstructor()) {
                dataStore.classifySSEF(codeUnit);
                return;
            }

            // If there is no field modification in the method and no further method call, the method is side effect free
            Set<JavaField> modifiedFields = retrieveModifyingFieldAccess(codeUnit);
            if (modifiedFields.isEmpty()) {
                dataStore.classifySSEF(codeUnit);
                return;
            }

            //If there are field modified but the state can't be accessed from the outside, the method is domain specific side effect free
            if (modifiedFieldsAreInternal(codeUnit, modifiedFields)) {
                dataStore.classifyDSEF(codeUnit);
                return;
            }

            //If there are field modifications that can accessed from the outside, the method is not side effect free
            if (!modifiedFieldsAreInternal(codeUnit, modifiedFields)) {
                dataStore.classifyNotSEF(codeUnit);
                logViolation(conditionEvents, codeUnit.getOwner(),
                        codeUnit.getFullName() + " is writing to at least one property");
                return;
            }
        }

        //If there are methods that are classified already, the current method may be derived from that
        for (JavaMethodCall call : calledMethods) {
            if (dataStore.isKnownNotSEF(call.getTarget().resolve()) && isVisibleToOuterScope(call, codeUnit)) {
                logViolation(conditionEvents, codeUnit.getOwner(), codeUnit.getFullName() + " calls not SEF method ( one of " + call.getTarget() + ")");
                dataStore.classifyNotSEF(codeUnit);
                return; //NotSEF is the hardest criteria, so we can stop here
            }
            if (dataStore.isUnsure(call.getTarget().resolve())) {
                dataStore.classifyUnsure(codeUnit);
            }
            if (!dataStore.isUnsure(call.getTarget().resolve()) && dataStore.isKnownDSEF(call.getTarget().resolve())) {
                dataStore.classifyDSEF(codeUnit);
            }
        }

        // There may be some uninvestigated cases. Classify them as unsure.
        dataStore.classifyUnsure(codeUnit);
    }

    private boolean modifiedFieldsAreInternal(JavaCodeUnit codeUnit, Set<JavaField> modifiedFields) {
        return modifiedFields.stream().allMatch(a -> a.getAccessesToSelf().stream().allMatch(b -> b.getOrigin().equals(codeUnit)));
    }

    private Set<JavaField> retrieveModifyingFieldAccess(JavaCodeUnit codeUnit) {
        // TODO KSC 25.02.22 is this sufficient? What about the increment example in https://medium.com/@jackel119/what-is-functional-programming-really-part-i-d1f4d54d69a1
        return codeUnit.getFieldAccesses().stream()
                .filter(fa -> fa.getAccessType().equals(JavaFieldAccess.AccessType.SET))
                .map(m -> m.getTarget().resolveField().orElse(null)).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * An operation which has no return parameters can not be SEF, because it is either changing its
     * parameters (call by reference), its local state, or it cannot do anything. so it is safe to classify as
     * not SEF. However, it may be considered as side effect free in case there may be some exception to be thrown as
     * long as it isn't thrown at runtime. As we can't check the runtime behaviour here, this decision is escalated to
     * the user. The behaviour can be configured with the option exceptionsConsideredAsSef. AS a user
     * you could also be quite strict, considering exceptions to be a side effect but preclassify the methods you
     * intentionally want to be considered as Side effect free.
     * Constructors omit their return type but should at least be designed to be side effect free.
     * *
     *
     * @param codeUnit the method to be checked
     * @return true if the method is considered to be not free of side effects, false otherwise
     */
    private boolean checkVoidMethodAsNotSef(JavaCodeUnit codeUnit) {
        return !codeUnit.isConstructor() &&
                codeUnit.getRawReturnType().getFullName().equals("void");
    }

    private boolean throwsException(JavaCodeUnit javaMethod) {
        return javaMethod.getConstructorCallsFromSelf()
                .stream()
                .anyMatch(javaConstructorCall -> javaConstructorCall.getTargetOwner().isAssignableTo(Exception.class));
    }

    private boolean validateMethodCalls(JavaCodeUnit codeUnit, ConditionEvents conditionEvents, PurenessClassification premilaryClassification) {
        Set<JavaMethodCall> callsToCheck = codeUnit.getMethodCallsFromSelf();

        for (JavaMethodCall call : callsToCheck) {
            if (dataStore.isUnsure(call.getTarget().resolve())) {
                return false;
            }
            if (dataStore.isKnownNotSEF(call.getTarget().resolve()) && isVisibleToOuterScope(call, codeUnit)) {
                logViolation(conditionEvents, codeUnit.getOwner(), codeUnit.getFullName() + "  calls not SEF method ( one of" + call.getTarget() + ")");
                dataStore.classifyNotSEF(codeUnit);
                return true;
            }
            if (dataStore.isKnownDSEF(call.getTarget().resolve())) {
                premilaryClassification = PurenessClassification.DSEF;
            }
        }

        dataStore.classifyAs(codeUnit, premilaryClassification);

        return true;
    }

    private boolean isVisibleToOuterScope(JavaMethodCall call, JavaCodeUnit codeUnit) {
        Set<JavaClass> internalInstantiations = codeUnit.getConstructorCallsFromSelf()
                .stream()
                .map(constructorCall -> constructorCall.getTarget().getOwner())
                .collect(Collectors.toUnmodifiableSet());
        return !internalInstantiations.contains(call.getTarget().getOwner());
    }

    private void logViolation(ConditionEvents conditionEvents, JavaClass owner, String meldung) {

        if (owner.getFullName().startsWith("hamburg.")) {
            conditionEvents.add(SimpleConditionEvent.violated(owner, meldung));
        }
    }

    @Override
    public void finish(ConditionEvents conditionEvents) {
        System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + interfaces.size());
        boolean hasChanged = true;
        while (hasChanged) {
            hasChanged = applyPropagationRules(conditionEvents);
            //TODO KSC 14.02.22: Use a logger
            System.out.println(dataStore.info() + " Anzahl offene Interfaces: " + interfaces.size());
        }
        dataStore.getAllMethodsOfClassification(PurenessClassification.UNSURE).forEach(un -> logUnsure(conditionEvents, un.getOwner(), un));
    }

    private boolean applyPropagationRules(ConditionEvents conditionEvents) {

        boolean hasChanged;
        Set<JavaCodeUnit> unchecked = dataStore.getAllMethodsOfClassification(PurenessClassification.UNCHECKED);

        hasChanged = !unchecked.isEmpty();
        //TODO KSC 16.02.22: Check why there varying unchecked code units (11 on start and changing on each iteration)
        for (JavaCodeUnit meth : unchecked) {
            collectAndPreClassify(meth, conditionEvents);
        }

        for (JavaCodeUnit meth : dataStore.getAllMethodsOfClassification(PurenessClassification.UNSURE)) {
            hasChanged |= validateMethodCalls(meth, conditionEvents, PurenessClassification.SSEF);
        }

        hasChanged |= checkInterfaces();
        return hasChanged;
    }

    private void logUnsure(ConditionEvents conditionEvents, JavaClass owner, JavaCodeUnit meth) {
        if (owner.getFullName().startsWith("app.")) {
            Set<JavaMethodCall> unsure = meth.getMethodCallsFromSelf().stream().filter(c -> !dataStore.isKnownNotSEF(c.getTarget().resolve()) && !dataStore.isKnownDSEF(c.getTarget().resolve())).collect(Collectors.toSet());
            conditionEvents.add(SimpleConditionEvent.violated(owner, "unsure about " + meth.getFullName() + " because of " + unsure));
        }
    }

    private boolean checkInterfaces() {
        Set<JavaCodeUnit> toRemove = new HashSet<>();
        for (JavaCodeUnit anInterface : interfaces) {
            if (anInterface.getOwner().getAllSubclasses().stream()
                    .allMatch(cl -> cl.getAllMethods().stream()
                            .filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes()))
                            .allMatch(dataStore::isKnownSSEF))) {
                dataStore.classifySSEF(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubclasses().stream()
                    .allMatch(cl -> cl.getAllMethods().stream()
                            .filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes()))
                            .allMatch(dataStore::isKnownAtLeastDSEF))) {
                dataStore.classifyDSEF(anInterface);
                toRemove.add(anInterface);
            } else if (anInterface.getOwner().getAllSubclasses().stream()
                    .anyMatch(cl -> cl.getAllMethods().stream()
                            .filter(f -> f.getName().equals(anInterface.getName()) && anInterface.getRawParameterTypes().equals(f.getRawParameterTypes()))
                            .anyMatch(dataStore::isKnownNotSEF))) {
                dataStore.isKnownNotSEF(anInterface);
                toRemove.add(anInterface);
            }
        }
        return interfaces.removeAll(toRemove);
    }

    @Override
    public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
        javaClass.getCodeUnits().forEach(javaConstructor -> collectAndPreClassify(javaConstructor, conditionEvents));
    }

    public PureDataStore getDataStore() {
        return dataStore;
    }
}

