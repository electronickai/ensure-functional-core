package playground;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnit;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DataStore {

    private final Set<JavaCodeUnit> KNOWN_STRICT_SEF_METH = new HashSet<>();
    private final Set<JavaCodeUnit> KNOWN_DSEF_METH = new HashSet<>();
    private final Set<JavaCodeUnit> KNOWN_NOT_SEF_METH = new HashSet<>();
    private final Set<JavaCodeUnit> UNSURE_METH = new HashSet<>();
    private final Set<String> NOT_SEF_API =  Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream");
    private final Set<String> DEF_DSEF_API = Set.of("java.util.logging.","java.util.function.BiConsumer");
    private final Set<String> DEF_SSEF_API = Set.of("java.lang.Object.clone()", "java.lang.Object.hashCode()", "java.lang.Object.toString()", "java.lang.Object.getClass()", "java.lang.Class.getSimpleName()", "java.lang.Class.privateGetPublicMethods()", "java.lang.Class.getGenericInfo()");


    boolean isKnownSSEF(JavaCodeUnit methode) {
        return  KNOWN_STRICT_SEF_METH.contains(methode) ||  DEF_SSEF_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    boolean isKnownSSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSSEF);
    }


    boolean isKnownDSEF(JavaCodeUnit methode) {
        return KNOWN_DSEF_METH.contains(methode) ||  DEF_DSEF_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    boolean isKnownAtLeastDSEF(JavaCodeUnit methode) {
        return KNOWN_DSEF_METH.contains(methode) || isKnownSSEF(methode);
    }

    boolean isKnownAtLeastDSEF(Collection<JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownAtLeastDSEF);
    }

    boolean isKnownDSEF(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownDSEF);
    }


    boolean isKnownNotSEF(JavaCodeUnit methode) {
        return KNOWN_NOT_SEF_METH.contains(methode) || NOT_SEF_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    boolean isKnownNotSEF(Collection<? extends  JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotSEF);
    }

    boolean alreadyClassified(JavaCodeUnit JavaCodeUnit) {
        if (isKnownSSEF(JavaCodeUnit) || isKnownDSEF(JavaCodeUnit) || isKnownNotSEF(JavaCodeUnit)) {
            UNSURE_METH.remove(JavaCodeUnit);
            return true;
        }
        return false;
    }

    public void classifyNotSEF(JavaCodeUnit JavaCodeUnit) {
        KNOWN_NOT_SEF_METH.add(JavaCodeUnit);
        UNSURE_METH.remove(JavaCodeUnit);
    }

    public void classifySSEF(JavaCodeUnit JavaCodeUnit) {
        KNOWN_STRICT_SEF_METH.add(JavaCodeUnit);
        UNSURE_METH.remove(JavaCodeUnit);
    }

    public void classifyDSEF(JavaCodeUnit JavaCodeUnit) {
        KNOWN_DSEF_METH.add(JavaCodeUnit);
        UNSURE_METH.remove(JavaCodeUnit);
    }

    public void classifyUnsure(JavaCodeUnit JavaCodeUnit) {
        UNSURE_METH.add(JavaCodeUnit);
    }

    public String info() {
        return "Anzahl SSEF:  " + KNOWN_STRICT_SEF_METH.size() + "  Anzahl DSEF: " + KNOWN_DSEF_METH.size() + "  Anzahl unsure: " + UNSURE_METH.size() + "  Anzahl NotSEF: " + KNOWN_NOT_SEF_METH.size() ;
    }

    public Set<JavaCodeUnit> getUnshureMethods() {
        return UNSURE_METH;
    }

    public Set<JavaCodeUnit> getUnshureMethodsClone() {
        return (HashSet<JavaCodeUnit>) ((HashSet<JavaCodeUnit>) UNSURE_METH).clone();
    }

    public boolean isUnsure(JavaCodeUnit JavaCodeUnit) {
        return UNSURE_METH.contains(JavaCodeUnit);
    }

    boolean isUnsure(Collection<? extends JavaCodeUnit> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    String getStringOfUnshure() {
        return UNSURE_METH.stream().map(JavaCodeUnit::getFullName).collect(Collectors.joining("\n"));
    }

    String getStringOfNotSEF() {
        return KNOWN_NOT_SEF_METH.stream().map(JavaCodeUnit::getFullName).collect(Collectors.joining("\n"));
    }

    String getStringOfSSEF() {
        return KNOWN_STRICT_SEF_METH.stream().map(JavaCodeUnit::getFullName).collect(Collectors.joining("\n"));
    }

    String getStringOfDSEF() {
        return KNOWN_DSEF_METH.stream().map(JavaCodeUnit::getFullName).collect(Collectors.joining("\n"));
    }

    String getClassificationFor(JavaCodeUnit JavaCodeUnit) {

        if(isUnsure(JavaCodeUnit)) {
            return "unsure";
        }
        if(isKnownNotSEF(JavaCodeUnit)) {
            return "not SEF";
        }
        if(isKnownSSEF(JavaCodeUnit)) {
            return "SSEF";
        }
        if(isKnownDSEF(JavaCodeUnit)) {
            return "DSEF";
        }
        return "not found";

    }


}
