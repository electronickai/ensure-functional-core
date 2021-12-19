package playground;

import com.tngtech.archunit.core.domain.JavaCodeUnit;

import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DetDataStore {

    private final HashMap<JavaCodeUnit, ClassificationEnum> classification = new HashMap<>();

    private final Set<String> NOT_DET_API = Set.of(
            "java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.",
            "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream", "java.lang.Runnable");
    private final Set<String> DEF_DDET_API = Set.of();
    private final Set<String> DEF_SDET_API = Set.of();

    boolean isKnownSDET(JavaCodeUnit codeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(codeUnit, ClassificationEnum.UNCHECKED);
        if (ClassificationEnum.UNCHECKED.equals(cl)) {
            if (DEF_SDET_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, ClassificationEnum.SDET);
                return true;
            }
        }
        return ClassificationEnum.SDET.equals(cl);
    }

    boolean isKnownSDET(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSDET);
    }


    boolean isKnownDDET(JavaCodeUnit codeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(codeUnit, ClassificationEnum.UNCHECKED);
        if (ClassificationEnum.UNCHECKED.equals(cl)) {
            if (DEF_DDET_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, ClassificationEnum.DDET);
                return true;
            }
        }
        return ClassificationEnum.DDET.equals(cl);
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

    boolean isKnownNotDET(JavaCodeUnit codeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(codeUnit, ClassificationEnum.UNCHECKED);
        if (ClassificationEnum.UNCHECKED.equals(cl)) {
            if (NOT_DET_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, ClassificationEnum.NOT_DET);
                return true;
            }
        }
        return ClassificationEnum.NOT_DET.equals(cl);
    }

    boolean isKnownNotDET(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotDET);
    }

    public boolean isUnsure(JavaCodeUnit javaCodeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(javaCodeUnit, ClassificationEnum.UNCHECKED);
        return ClassificationEnum.UNSURE.equals(cl) || ClassificationEnum.UNCHECKED.equals(cl);
    }

    boolean isUnsure(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    boolean alreadyClassified(JavaCodeUnit JavaCodeUnit) {
        return (isKnownSDET(JavaCodeUnit) || isKnownDDET(JavaCodeUnit) || isKnownNotDET(JavaCodeUnit));

    }

    public void classifyNotDET(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.NOT_DET);
    }

    public void classifySDET(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.SDET);
    }

    public void classifyDDET(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.DDET);
    }

    public void classifyUnsure(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.UNSURE);
    }

    public String info() {

        int sdef = 0;
        int ddef = 0;
        int ndef = 0;
        int us = 0;
        int uc = 0;

        for (Map.Entry<JavaCodeUnit, ClassificationEnum> entry : classification.entrySet()) {
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
        return fo.format("Gesamt %d6 Anzahl SDET:  %d6  Anzahl DDET: %d6  Anzahl unsure: %d6  Anzahl NotDET:  %d6  Anzahl UNKOWN: %d6", classification.size(), sdef, ddef, us, ndef, uc).toString();
    }

    public Set<JavaCodeUnit> getClMethods(ClassificationEnum cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    String getOfClassification(ClassificationEnum cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey).map(JavaCodeUnit::getFullName)
                .collect(Collectors.joining("\n"));
    }

    String getClassificationFor(JavaCodeUnit codeUnit) {
        return classification.getOrDefault(codeUnit, ClassificationEnum.UNCHECKED).toString();
    }

    enum ClassificationEnum {
        UNCHECKED("unchecked (DET)"),
        UNSURE("unsure (DET)"),
        NOT_DET("not DET"),
        SDET("SDET"),
        DDET("DET");

        private final String displayName;

        ClassificationEnum(String ds) {
            displayName = ds;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }


}
