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

  private final Set<String> SSEF_API_PREFIXES =
      Set.of("java.lang.Object.clone()", "java.lang.Object.hashCode()",
          "java.lang.Object.toString()", "java.lang.Object.getClass()",
          "java.lang.Class.getSimpleName()", "java.lang.Class.privateGetPublicMethods()",
          "java.lang.Class.getGenericInfo()");
  private final Set<String> DSEF_API_PREFIXES =
      Set.of("java.util.logging.", "java.util.function.BiConsumer");
  private final Set<String> NOT_SEF_API_PREFIXES =
      Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.",
          "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.",
          "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.",
          "java.lang.invoke.");

  public PurenessClassification getClassificationFor(JavaCodeUnit javaCodeUnit) {
    return classification.getOrDefault(javaCodeUnit, PurenessClassification.UNCHECKED);
  }

  public boolean checkToBeSSEF(JavaCodeUnit codeUnit) {
    return PurenessClassification.SSEF.equals(getClassification(codeUnit));
  }

  boolean checkContainSSEF(Collection<? extends JavaCodeUnit> methods) {
    return !methods.isEmpty() && methods.stream().anyMatch(this::checkToBeSSEF);
  }

  boolean checkToBeAtLeastSSEF(JavaCodeUnit codeUnit) {
    return getClassification(codeUnit).isAtLeast(PurenessClassification.SSEF);
  }

  boolean checkToBeAtLeastSSEF(Collection<? extends JavaCodeUnit> methods) {
    return !methods.isEmpty() && methods.stream().anyMatch(this::checkToBeAtLeastSSEF);
  }

  public boolean checkToBeDSEF(JavaCodeUnit codeUnit) {
    return PurenessClassification.DSEF.equals(getClassification(codeUnit));
  }

  boolean checkContainDSEF(Collection<? extends JavaCodeUnit> methods) {
    return !methods.isEmpty() && methods.stream().anyMatch(this::checkToBeDSEF);
  }

  boolean checkToBeAtLeastDSEF(JavaCodeUnit codeUnit) {
    return getClassification(codeUnit).isAtLeast(PurenessClassification.DSEF);
  }

  boolean checkToBeAtLeastDSEF(Collection<? extends JavaCodeUnit> methods) {
    return !methods.isEmpty() && methods.stream().anyMatch(this::checkToBeAtLeastDSEF);
  }

  public boolean checkToBeNotSEF(JavaCodeUnit codeUnit) {
    return PurenessClassification.NOT_SEF.equals(getClassification(codeUnit));
  }

  boolean checkContainNotSEF(Collection<? extends JavaCodeUnit> methods) {
    return !methods.isEmpty() && methods.stream().anyMatch(this::checkToBeNotSEF);
  }

  public boolean checkToBeUnsure(JavaCodeUnit codeUnit) {
    return PurenessClassification.UNSURE.isAtLeast(getClassification(codeUnit));
  }

  boolean checkContainUnsure(Collection<? extends JavaCodeUnit> methods) {
    return !methods.isEmpty() && methods.stream().anyMatch(this::checkToBeUnsure);
  }

  void classifySSEF(JavaCodeUnit javaCodeUnit) {
    classification.put(javaCodeUnit, PurenessClassification.SSEF);
  }

  void classifyDSEF(JavaCodeUnit javaCodeUnit) {
    classification.put(javaCodeUnit, PurenessClassification.DSEF);
  }

  void classifyNotSEF(JavaCodeUnit javaCodeUnit) {
    classification.put(javaCodeUnit, PurenessClassification.NOT_SEF);
  }

  void classifyUnsure(JavaCodeUnit javaCodeUnit) {
    classification.put(javaCodeUnit, PurenessClassification.UNSURE);
  }

  boolean alreadyClassified(JavaCodeUnit codeUnit) {
    return !getClassification(codeUnit).isTemporaryClassification();
  }

  Set<JavaCodeUnit> getAllMethodsOfClassification(PurenessClassification cl) {
    return classification.entrySet().stream()
        .filter(m -> m.getValue().equals(cl))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  String countCategories() {

    int ssef = 0;
    int dsef = 0;
    int not_sef = 0;
    int unsure = 0;
    int unchecked = 0;

    for (Map.Entry<JavaCodeUnit, PurenessClassification> entry : classification.entrySet()) {
      switch (entry.getValue()) {
        case SSEF:
          ssef++;
          break;
        case DSEF:
          dsef++;
          break;
        case NOT_SEF:
          not_sef++;
          break;
        case UNSURE:
          unsure++;
          break;
        case UNCHECKED:
          unchecked++;
          break;
      }
    }

    Formatter fo = new Formatter();
    return fo.format(
        "Gesamt %d Anzahl SSEF:  %d  Anzahl DSEF: %d  Anzahl unsure: %d  Anzahl NotSEF:  %d  Anzahl UNKOWN: %d",
        classification.size(), ssef, dsef, unsure, not_sef, unchecked).toString();
  }

  private boolean isPrefixOf(JavaCodeUnit codeUnit, Set<String> SSEF_API_PREFIXES) {
    return SSEF_API_PREFIXES.stream().anyMatch(a -> codeUnit.getFullName().startsWith(a));
  }

  private PurenessClassification getClassification(JavaCodeUnit codeUnit) {
    return classification.computeIfAbsent(codeUnit, this::tryToApplyPreconfiguredClassication);
  }

  /**
   * Calculate the default classification depending on the configured predefined classifications
   *
   * @param codeUnit CodeUnit fo witch the default is calculated
   * @return the current classification of the codeunit
   */
  private PurenessClassification tryToApplyPreconfiguredClassication(JavaCodeUnit codeUnit) {
    if (isPrefixOf(codeUnit, SSEF_API_PREFIXES)) {
      return PurenessClassification.SSEF;
    } else if (isPrefixOf(codeUnit, DSEF_API_PREFIXES)) {
      return PurenessClassification.DSEF;
    } else if (isPrefixOf(codeUnit, NOT_SEF_API_PREFIXES)) {
      return PurenessClassification.NOT_SEF;
    } else {
      return PurenessClassification.UNCHECKED;
    }
  }
}
