package playground.pureness;

import java.util.EnumSet;

public enum PurenessClassification {
    UNCHECKED("unchecked (SEF)", true),
    UNSURE("unsure (SEF)", true),
    NOT_SEF("not SEF", false),
    SSEF("strictly SEF", false),
    DSEF("domain SEF", false),
    PURE("pure", false);

    private final String displayName;
    private final boolean isTemporaryClassification;
    private EnumSet<PurenessClassification> implies;

    PurenessClassification(String ds, boolean isTemporary) {
        displayName = ds;
        isTemporaryClassification = isTemporary;
    }

    public boolean isAtLeast(PurenessClassification classification) {
        if(implies == null) {
            implies = initImplies();
        }
        return implies.contains(classification);
    }

    public boolean isTemporaryClassification() {
        return isTemporaryClassification;
    }

    private EnumSet<PurenessClassification>  initImplies() {
        switch (this) {
            case PURE:
                return EnumSet.of(PURE, DSEF, SSEF);
            case SSEF:
                return EnumSet.of(DSEF, SSEF);
            case DSEF:
                return EnumSet.of(DSEF);
            case UNSURE:
                return EnumSet.of(UNSURE, UNCHECKED);
            case NOT_SEF:
                return EnumSet.of(NOT_SEF);
            case UNCHECKED:
                return EnumSet.of(UNCHECKED);
            default:
                throw new IllegalArgumentException("Enum values not fully defines. This is a development issues and should not happen.");
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
