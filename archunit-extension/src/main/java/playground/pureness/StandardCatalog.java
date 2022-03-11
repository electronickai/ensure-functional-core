package playground.pureness;

import java.util.Set;

public class StandardCatalog {

  private static final Set<String> SSEF_PREFIXES =
      Set.of("java.lang.Object.clone()", "java.lang.Object.hashCode()",
          "java.lang.Object.toString()", "java.lang.Object.getClass()",
          "java.lang.Class.getSimpleName()", "java.lang.Class.privateGetPublicMethods()",
          "java.lang.Class.getGenericInfo()");

  private static final Set<String> DSEF_PREFIXES =
      Set.of("java.util.logging.", "java.util.function.BiConsumer");

  private static final Set<String> NOT_SEF_PREFIXES =
      Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.",
          "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.",
          "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.",
          "java.lang.invoke.");

  public static Set<String> getSsefPrefixes() {
    return SSEF_PREFIXES;
  }

  public static Set<String> getDsefPrefixes() {
    return DSEF_PREFIXES;
  }

  public static Set<String> getNotSefPrefixes() {
    return NOT_SEF_PREFIXES;
  }
}
