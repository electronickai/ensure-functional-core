package playground;

import com.tngtech.archunit.core.domain.JavaMethod;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DataStore {

    private final Set<JavaMethod> KNOWN_STRICT_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_DSEF_METH = new HashSet<>();
    private final Set<JavaMethod> KNOWN_NOT_SEF_METH = new HashSet<>();
    private final Set<JavaMethod> UNSURE_METH = new HashSet<>();
    private final Set<String> NOT_SEF_API = Set.of("java.io.", "java.nio.", "java.reflect.", "jdk.internal.", "sun.management.", "sun.reflect.", "java.net.", "java.security.", "javax.xml", "sun.invoke.", "javax.management.", "org.w3c.", "java.util.concurrent.", "java.util.logging.", "java.lang.invoke.", "java.util.stream");


    boolean isKnownSSEF(JavaMethod methode) {
        return methode.isConstructor() || KNOWN_STRICT_SEF_METH.contains(methode);
    }

    boolean isKnownSSEF(Collection<JavaMethod> methods) {
        return methods.isEmpty() || methods.stream().anyMatch(this::isKnownSSEF);
    }


    boolean isKnownDSEF(JavaMethod methode) {
        return KNOWN_DSEF_METH.contains(methode) || isKnownSSEF(methode);
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

    public String info() {
        return "Anzahl strict SEF:  " + KNOWN_STRICT_SEF_METH.size() + "  Anzahl SEF: " + KNOWN_DSEF_METH.size() + "  Anzahl unsure: " + UNSURE_METH.size() + "  Anzahl not SEF: " + KNOWN_NOT_SEF_METH.size() ;
    }

    public Set<JavaMethod> getUnshureMethods() {
        return UNSURE_METH;
    }

    public Set<JavaMethod> getUnshureMethodsClone() {
        return (HashSet<JavaMethod>) ((HashSet<JavaMethod>) UNSURE_METH).clone();
    }

    public void classifyUnsure(JavaMethod javaMethod) {
        UNSURE_METH.add(javaMethod);
    }
}
