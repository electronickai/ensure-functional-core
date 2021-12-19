package playground.deterministic;

import com.tngtech.archunit.core.domain.JavaCodeUnit;

import java.util.*;
import java.util.stream.Collectors;

public class DetDataStore {

    private final HashMap<JavaCodeUnit, DeterministicClassification> classification = new HashMap<>();

    private final Set<String> NOT_DET_API = Set.of(
            "java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.",
            "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream", "java.lang.Runnable");
    private final Set<String> DEF_DDET_API = Set.of();
    private final Set<String> DEF_SDET_API = Set.of();

    public boolean isKnownSDET(JavaCodeUnit codeUnit) {
        DeterministicClassification cl = classification.putIfAbsent(codeUnit, DeterministicClassification.UNCHECKED);
        if (DeterministicClassification.UNCHECKED.equals(cl)) {
            if (DEF_SDET_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, DeterministicClassification.SDET);
                return true;
            }
        }
        return DeterministicClassification.SDET.equals(cl);
    }

    boolean isKnownSDET(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSDET);
    }


    private boolean isKnownDDET(JavaCodeUnit codeUnit) {
        DeterministicClassification cl = classification.putIfAbsent(codeUnit, DeterministicClassification.UNCHECKED);
        if (DeterministicClassification.UNCHECKED.equals(cl)) {
            if (DEF_DDET_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, DeterministicClassification.DDET);
                return true;
            }
        }
        return DeterministicClassification.DDET.equals(cl);
    }

    boolean isKnownAtLeastDDET(JavaCodeUnit codeUnit) {
        return isKnownDDET(codeUnit) || isKnownSDET(codeUnit);
    }

    boolean isKnownAtLeastDDET(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownAtLeastDDET);
    }

    boolean isKnownDDET(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownDDET);
    }

    public boolean isKnownNotDET(JavaCodeUnit codeUnit) {
        DeterministicClassification cl = classification.putIfAbsent(codeUnit, DeterministicClassification.UNCHECKED);
        if (DeterministicClassification.UNCHECKED.equals(cl)) {
            if (NOT_DET_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, DeterministicClassification.NOT_DET);
                return true;
            }
        }
        return DeterministicClassification.NOT_DET.equals(cl);
    }

    boolean isKnownNotDET(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotDET);
    }

    boolean isUnsure(JavaCodeUnit javaCodeUnit) {
        DeterministicClassification cl = classification.putIfAbsent(javaCodeUnit, DeterministicClassification.UNCHECKED);
        return DeterministicClassification.UNSURE.equals(cl) || DeterministicClassification.UNCHECKED.equals(cl);
    }

    boolean isUnsure(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    boolean alreadyClassified(JavaCodeUnit JavaCodeUnit) {
        return (isKnownSDET(JavaCodeUnit) || isKnownDDET(JavaCodeUnit) || isKnownNotDET(JavaCodeUnit));
    }

    void classifyNotDET(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, DeterministicClassification.NOT_DET);
    }

    void classifySDET(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, DeterministicClassification.SDET);
    }

    void classifyDDET(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, DeterministicClassification.DDET);
    }

    void classifyUnsure(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, DeterministicClassification.UNSURE);
    }

    String info() {

        int sdef = 0;
        int ddef = 0;
        int ndef = 0;
        int us = 0;
        int uc = 0;

        for (Map.Entry<JavaCodeUnit, DeterministicClassification> entry : classification.entrySet()) {
            switch (entry.getValue()) {
                case SDET:
                    sdef++;
                    break;
                case DDET:
                    ddef++;
                    break;
                case NOT_DET:
                    ndef++;
                    break;
                case UNSURE:
                    us++;
                    break;
                case UNCHECKED:
                    uc++;
                    break;
            }
        }

        Formatter fo = new Formatter();
        return fo.format("Gesamt %d Anzahl SDET: %d  Anzahl DDET: %d  Anzahl unsure: %d  Anzahl NotDET: %d  Anzahl UNKOWN: %d", classification.size(), sdef, ddef, us, ndef, uc).toString();
    }

    Set<JavaCodeUnit> getClMethods(DeterministicClassification cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    String getOfClassification(DeterministicClassification cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey).map(JavaCodeUnit::getFullName)
                .collect(Collectors.joining("\n"));
    }

    public String getClassificationFor(JavaCodeUnit codeUnit) {
        return classification.getOrDefault(codeUnit, DeterministicClassification.UNCHECKED).toString();
    }
}
