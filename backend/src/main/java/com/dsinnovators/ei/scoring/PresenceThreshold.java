package com.dsinnovators.ei.scoring;

/** The share of a frequent evidence type that must sit at a tier or higher, as an exact fraction (spec 9.3). */
public record PresenceThreshold(int numerator, int denominator) {

    public PresenceThreshold {
        if (numerator < 1 || denominator < numerator) {
            throw new IllegalArgumentException("A presence threshold is a fraction above zero and not above one, got "
                    + numerator + "/" + denominator);
        }
    }

    /** True when {@code count} of {@code total} items reach the threshold, compared without rounding. */
    public boolean isReachedBy(long count, long total) {
        return count * denominator >= total * numerator;
    }
}
