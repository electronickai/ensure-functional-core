package playground.pureness;

import com.tngtech.archunit.core.domain.JavaCodeUnit;

import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PureDataStore {

    private final HashMap<JavaCodeUnit, PurenessClassification> classification = new HashMap<>();

    private final Set<String> NOT_SEF_API = Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.");
    private final Set<String> DEF_DSEF_API = Set.of("java.util.logging.", "java.util.function.BiConsumer");
    private final Set<String> DEF_SSEF_API = Set.of("java.lang.Object.clone()", "java.lang.Object.hashCode()", "java.lang.Object.toString()", "java.lang.Object.getClass()", "java.lang.Class.getSimpleName()", "java.lang.Class.privateGetPublicMethods()", "java.lang.Class.getGenericInfo()");

    /**
     * Calculate the default classification depending on the configured predefined classifications
     *
     * @param codeUnit CodeUnit fo witch the default is calculated
     * @return the current classification of the codeunit
     */
    private PurenessClassification preclassifyIfAbsent(JavaCodeUnit codeUnit) {
        if (DEF_SSEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
            return PurenessClassification.SSEF;
        } else if (DEF_DSEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
            return PurenessClassification.DSEF;
        } else if (NOT_SEF_API.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a))) {
            return PurenessClassification.NOT_SEF;
        } else {
            return PurenessClassification.UNCHECKED;
        }
    }

    private PurenessClassification getClassification(JavaCodeUnit codeUnit) {
        return classification.computeIfAbsent(codeUnit, this::preclassifyIfAbsent);
    }

    public boolean isKnownSSEF(JavaCodeUnit codeUnit) {
        return PurenessClassification.SSEF.equals(getClassification(codeUnit));
    }

    boolean isKnownSSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSSEF);
    }


    public boolean isKnownDSEF(JavaCodeUnit codeUnit) {
        return PurenessClassification.DSEF.equals(getClassification(codeUnit));
    }

    boolean isKnownAtLeastDSEF(JavaCodeUnit codeUnit) {
        return getClassification(codeUnit).isAtLeast(PurenessClassification.DSEF);
    }

    boolean isKnownAtLeastDSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownAtLeastDSEF);
    }

    boolean isKnownDSEF(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownDSEF);
    }

    public boolean isKnownNotSEF(JavaCodeUnit codeUnit) {
        return PurenessClassification.NOT_SEF.equals(getClassification(codeUnit));
    }

    boolean isKnownNotSEF(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotSEF);
    }

    public boolean isUnsure(JavaCodeUnit codeUnit) {
        return PurenessClassification.UNSURE.isAtLeast(getClassification(codeUnit));
    }

    boolean isUnsure(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    boolean alreadyClassified(JavaCodeUnit codeUnit) {
        return !getClassification(codeUnit).isTemporäryClassification();

    }

    void classifyNotSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, PurenessClassification.NOT_SEF);
    }

    void classifySSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, PurenessClassification.SSEF);
    }

    void classifyDSEF(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, PurenessClassification.DSEF);
    }

    void classifyUnsure(JavaCodeUnit javaCodeUnit) {
        classification.put(javaCodeUnit, PurenessClassification.UNSURE);
    }

    void classifyAs(JavaCodeUnit javaCodeUnit, PurenessClassification cl) {
        classification.put(javaCodeUnit, cl);
    }

    /**
     *
     * @param cl
     * @return
     */
    Set<JavaCodeUnit> getAllMethodsOfClassification(PurenessClassification cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /* Helpers for Debugging */

    String info() {

        int ssef = 0;
        int dsef = 0;
        int nsef = 0;
        int us = 0;
        int uc = 0;

        for (Map.Entry<JavaCodeUnit, PurenessClassification> entry : classification.entrySet()) {
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

    String getOfClassification(PurenessClassification cl) {
        return classification.entrySet().stream()
                .filter(m -> m.getValue().equals(cl))
                .map(Map.Entry::getKey).map(JavaCodeUnit::getFullName)
                .collect(Collectors.joining("\n"));
    }

    public String getClassificationFor(JavaCodeUnit javaCodeUnit) {
        return classification.getOrDefault(javaCodeUnit, PurenessClassification.UNCHECKED).toString();
    }
}
