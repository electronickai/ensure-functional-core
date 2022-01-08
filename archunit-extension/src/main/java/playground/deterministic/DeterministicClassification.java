package playground.deterministic;

public enum DeterministicClassification {
    UNCHECKED("unchecked (DET)"),
    UNSURE("unsure (DET)"),
    NOT_DET("not DET"),
    SDET("SDET"),
    DDET("DET");

    private final String displayName;

    DeterministicClassification(String ds) {
        displayName = ds;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
