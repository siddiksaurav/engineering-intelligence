package com.dsinnovators.ei.scoring;

/** The level a viewer selects to read a score against (spec 9.1 step 5). */
public enum Level {
    L1,
    L2,
    L3;

    /** The tier that has the same number as this level, for example T2 for L2. */
    public Tier sameNumberTier() {
        return switch (this) {
            case L1 -> Tier.T1;
            case L2 -> Tier.T2;
            case L3 -> Tier.T3;
        };
    }

    /** True when this level is {@code other} or higher. */
    public boolean isAtLeast(Level other) {
        return compareTo(other) >= 0;
    }
}
