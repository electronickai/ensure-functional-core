package playground;

import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DataStore {

    private final Set<JavaMethod> KNOWN_STRICT_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_DSEF_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_NOT_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> UNSURE_METH = new HashSet<>();
    private final Set<String> NOT_SEF_API =  Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream");
    private final Set<String> DEF_DSEF_API = Set.of("java.util.logging.","java.util.function.BiConsumer");


    boolean isKnownSSEF(JavaMethod methode) {
        return  KNOWN_STRICT_SEF_METH.contains(methode);
    }

    boolean isKnownSSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSSEF);
    }


    boolean isKnownDSEF(JavaMethod methode) {
        return KNOWN_DSEF_METH.contains(methode) || DEF_DSEF_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    boolean isKnownAtLeastDSEF(JavaMethod methode) {
        return KNOWN_DSEF_METH.contains(methode) || isKnownSSEF(methode);
    }

    boolean isKnownAtLeastDSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownAtLeastDSEF);
    }

    boolean isKnownDSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownDSEF);
    }


    boolean isKnownNotSEF(JavaMethod methode) {
        return KNOWN_NOT_SEF_METH.contains(methode) || NOT_SEF_API.stream().anyMatch(a -> methode.getFullName().startsWith(a));
    }

    boolean isKnownNotSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownNotSEF);
    }

    boolean alreadyClassified(JavaMethod javaMethod) {
        if (isKnownSSEF(javaMethod) || isKnownDSEF(javaMethod) || isKnownNotSEF(javaMethod)) {
            UNSURE_METH.remove(javaMethod);
            return true;
        }
        return false;
    }

    public void classifyNotSEF(JavaMethod javaMethod) {
        KNOWN_NOT_SEF_METH.add(javaMethod);
        UNSURE_METH.remove(javaMethod);
    }

    public void classifySSEF(JavaMethod javaMethod) {
        KNOWN_STRICT_SEF_METH.add(javaMethod);
        UNSURE_METH.remove(javaMethod);
    }

    public void classifyDSEF(JavaMethod javaMethod) {
        KNOWN_DSEF_METH.add(javaMethod);
        UNSURE_METH.remove(javaMethod);
    }

    public void classifyUnsure(JavaMethod javaMethod) {
        UNSURE_METH.add(javaMethod);
    }

    public String info() {
        return "Anzahl SSEF:  " + KNOWN_STRICT_SEF_METH.size() + "  Anzahl DSEF: " + KNOWN_DSEF_METH.size() + "  Anzahl unsure: " + UNSURE_METH.size() + "  Anzahl NotSEF: " + KNOWN_NOT_SEF_METH.size() ;
    }

    public Set<JavaMethod> getUnshureMethods() {
        return UNSURE_METH;
    }

    public Set<JavaMethod> getUnshureMethodsClone() {
        return (HashSet<JavaMethod>) ((HashSet<JavaMethod>) UNSURE_METH).clone();
    }

    public boolean isUnsure(JavaMethod javaMethod) {
        return UNSURE_METH.contains(javaMethod);
    }

    boolean isUnsure(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isUnsure);
    }

    String getStringOfUnshure() {
        return UNSURE_METH.stream().map(JavaCodeUnit::getFullName).collect(Collectors.joining("\n"));
    }

    String getStringOfNotSEF() {
        return KNOWN_NOT_SEF_METH.stream().map(JavaCodeUnit::getFullName).collect(Collectors.joining("\n"));
    }

    String getClassificationFor(JavaMethod javaMethod) {

        if(isUnsure(javaMethod)) {
            return "unsure";
        }
        if(isKnownNotSEF(javaMethod)) {
            return "not SEF";
        }
        if(isKnownSSEF(javaMethod)) {
            return "SSEF";
        }
        if(isKnownDSEF(javaMethod)) {
            return "DSEF";
        }
        return "not found";

    }


}
