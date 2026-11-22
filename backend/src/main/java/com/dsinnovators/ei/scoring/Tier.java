package com.dsinnovators.ei.scoring;

import java.util.Locale;

/** A tier of an evidence item, from the lowest to the highest. The order of the constants is the order of the tiers. */
public enum Tier {
    T1,
    T2,
    T3;

    /** True when this tier is {@code other} or higher. */
    public boolean isAtLeast(Tier other) {
        return compareTo(other) >= 0;
    }

    /** The tier as it is stored in {@code judgment.tier}. */
    public static Tier fromDatabase(String text) {
        return valueOf(text.toUpperCase(Locale.ROOT));
    }
}
