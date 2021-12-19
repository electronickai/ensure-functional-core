package playground.sideeffectfree;

public enum SideEffectFreeClassification {
    UNCHECKED("unchecked (SEF)"),
    UNSURE("unsure (SEF)"),
    NOT_SEF("not SEF"),
    SSEF("strictly SEF"),
    DSEF("domain SEF");

    private final String displayName;

    SideEffectFreeClassification(String ds) {
        displayName = ds;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
