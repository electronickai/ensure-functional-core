package playground;

import com.tngtech.archunit.core.domain.JavaCodeUnit;

import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SefDataStore {

    private final HashMap<JavaCodeUnit, ClassificationEnum> classification = new HashMap<>();

    private final Set<String> NOT_SEF_API = Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream");
    private final Set<String> DEF_DSEF_API = Set.of("java.util.logging.", "java.util.function.BiConsumer");
    private final Set<String> DEF_SSEF_API = Set.of("java.lang.Object.clone()", "java.lang.Object.hashCode()", "java.lang.Object.toString()", "java.lang.Object.getClass()", "java.lang.Class.getSimpleName()", "java.lang.Class.privateGetPublicMethods()", "java.lang.Class.getGenericInfo()");

    boolean isKnownSSEF(JavaCodeUnit codeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(codeUnit, ClassificationEnum.UNCHECKED);
        if (ClassificationEnum.UNCHECKED.equals(cl)) {
            if (DEF_SSEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, ClassificationEnum.SSEF);
                return true;
            }
        }
        return ClassificationEnum.SSEF.equals(cl);
    }

    boolean isKnownSSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSSEF);
    }


    boolean isKnownDSEF(JavaCodeUnit codeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(codeUnit, ClassificationEnum.UNCHECKED);
        if (ClassificationEnum.UNCHECKED.equals(cl)) {
            if (DEF_DSEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, ClassificationEnum.DSEF);
                return true;
            }
        }
        return ClassificationEnum.DSEF.equals(cl);
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


    boolean isKnownNotSEF(JavaCodeUnit codeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(codeUnit, ClassificationEnum.UNCHECKED);
        if (ClassificationEnum.UNCHECKED.equals(cl)) {
            if (NOT_SEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
                classification.put(codeUnit, ClassificationEnum.NOT_SEF);
                return true;
            }
        }
        return ClassificationEnum.NOT_SEF.equals(cl);
    }

    boolean isKnownNotSEF(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotSEF);
    }

    public boolean isUnsure(JavaCodeUnit javaCodeUnit) {
        ClassificationEnum cl = classification.putIfAbsent(javaCodeUnit, ClassificationEnum.UNCHECKED);
        return ClassificationEnum.UNSURE.equals(cl) || ClassificationEnum.UNCHECKED.equals(cl);
    }

    boolean isUnsure(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    boolean alreadyClassified(JavaCodeUnit JavaCodeUnit) {
        return (isKnownSSEF(JavaCodeUnit) || isKnownDSEF(JavaCodeUnit) || isKnownNotSEF(JavaCodeUnit));

    }

    public void classifyNotSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.NOT_SEF);
    }

    public void classifySSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.SSEF);
    }

    public void classifyDSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.DSEF);
    }

    public void classifyUnsure(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, ClassificationEnum.UNSURE);
    }

    public String info() {

        int ssef = 0;
        int dsef = 0;
        int nsef = 0;
        int us = 0;
        int uc = 0;

        for (Map.Entry<JavaCodeUnit, ClassificationEnum> entry : classification.entrySet()) {
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
        return  fo.format("Gesamt %d6 Anzahl SSEF:  %d6  Anzahl DSEF: %d6  Anzahl unsure: %d6  Anzahl NotSEF:  %d6  Anzahl UNKOWN: %d6" , classification.size(), ssef, dsef, us,  nsef, uc).toString();
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

    String getClassificationFor(JavaCodeUnit javaCodeUnit) {
        return classification.getOrDefault(javaCodeUnit, ClassificationEnum.UNCHECKED).toString();
    }

    enum ClassificationEnum {
        UNCHECKED("unchecked (SEF)"),
        UNSURE("unsure (SEF)"),
        NOT_SEF("not SEF"),
        SSEF("strictly SEF"),
        DSEF("domain SEF");

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
