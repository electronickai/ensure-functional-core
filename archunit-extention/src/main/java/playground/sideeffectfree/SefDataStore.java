package playground.sideeffectfree;

import com.tngtech.archunit.core.domain.JavaCodeUnit;

import java.util.*;
import java.util.stream.Collectors;

public class SefDataStore {

    private final HashMap<JavaCodeUnit, SideEffectFreeClassification> classification = new HashMap<>();

    private final Set<String> NOT_SEF_API = Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream");
    private final Set<String> DEF_DSEF_API = Set.of("java.util.logging.", "java.util.function.BiConsumer");
    private final Set<String> DEF_SSEF_API = Set.of("java.lang.Object.clone()", "java.lang.Object.hashCode()", "java.lang.Object.toString()", "java.lang.Object.getClass()", "java.lang.Class.getSimpleName()", "java.lang.Class.privateGetPublicMethods()", "java.lang.Class.getGenericInfo()");

    public boolean isKnownSSEF(JavaCodeUnit codeUnit) {
        SideEffectFreeClassification cl = classification.putIfAbsent(codeUnit, SideEffectFreeClassification.UNCHECKED);
        if (SideEffectFreeClassification.UNCHECKED.equals(cl)) {
            if (DEF_SSEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, SideEffectFreeClassification.SSEF);
                return true;
            }
        }
        return SideEffectFreeClassification.SSEF.equals(cl);
    }

    boolean isKnownSSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSSEF);
    }


    public boolean isKnownDSEF(JavaCodeUnit codeUnit) {
        SideEffectFreeClassification cl = classification.putIfAbsent(codeUnit, SideEffectFreeClassification.UNCHECKED);
        if (SideEffectFreeClassification.UNCHECKED.equals(cl)) {
            if (DEF_DSEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, SideEffectFreeClassification.DSEF);
                return true;
            }
        }
        return SideEffectFreeClassification.DSEF.equals(cl);
    }

    boolean isKnownAtLeastDSEF(JavaCodeUnit codeUnit) {
        return isKnownDSEF(codeUnit) || isKnownSSEF(codeUnit);
    }

    boolean isKnownAtLeastDSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownAtLeastDSEF);
    }

    boolean isKnownDSEF(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownDSEF);
    }


    public boolean isKnownNotSEF(JavaCodeUnit codeUnit) {
        SideEffectFreeClassification cl = classification.putIfAbsent(codeUnit, SideEffectFreeClassification.UNCHECKED);
        if (SideEffectFreeClassification.UNCHECKED.equals(cl)) {
            if (NOT_SEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, SideEffectFreeClassification.NOT_SEF);
                return true;
            }
        }
        return SideEffectFreeClassification.NOT_SEF.equals(cl);
    }

    boolean isKnownNotSEF(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotSEF);
    }

    public boolean isUnsure(JavaCodeUnit javaCodeUnit) {
        SideEffectFreeClassification cl = classification.putIfAbsent(javaCodeUnit, SideEffectFreeClassification.UNCHECKED);
        return SideEffectFreeClassification.UNSURE.equals(cl) || SideEffectFreeClassification.UNCHECKED.equals(cl);
    }

    boolean isUnsure(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    boolean alreadyClassified(JavaCodeUnit JavaCodeUnit) {
        return (isKnownSSEF(JavaCodeUnit) || isKnownDSEF(JavaCodeUnit) || isKnownNotSEF(JavaCodeUnit));

    }

    void classifyNotSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, SideEffectFreeClassification.NOT_SEF);
    }

    void classifySSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, SideEffectFreeClassification.SSEF);
    }

    void classifyDSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, SideEffectFreeClassification.DSEF);
    }

    void classifyUnsure(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, SideEffectFreeClassification.UNSURE);
    }

    String info() {

        int ssef = 0;
        int dsef = 0;
        int nsef = 0;
        int us = 0;
        int uc = 0;

        for (Map.Entry<JavaCodeUnit, SideEffectFreeClassification> entry : classification.entrySet()) {
            switch (entry.getValue()) {
                case SSEF:
                    ssef++;
                    break;
                case DSEF:
                    dsef++;
                    break;
                case NOT_SEF:
                    nsef++;
                    break;
                case UNSURE:
                    us++;
                    break;
                case UNCHECKED:
                    uc++;
                    break;
            }
        }

        Formatter fo =  new Formatter();
        return fo.format("Gesamt %d Anzahl SSEF:  %d  Anzahl DSEF: %d  Anzahl unsure: %d  Anzahl NotSEF:  %d  Anzahl UNKOWN: %d", classification.size(), ssef, dsef, us, nsef, uc).toString();
    }

    Set<JavaCodeUnit> getClMethods(SideEffectFreeClassification cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    String getOfClassification(SideEffectFreeClassification cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey).map(JavaCodeUnit::getFullName)
                .collect(Collectors.joining("\n"));
    }

    public String getClassificationFor(JavaCodeUnit javaCodeUnit) {
        return classification.getOrDefault(javaCodeUnit, SideEffectFreeClassification.UNCHECKED).toString();
    }
}
