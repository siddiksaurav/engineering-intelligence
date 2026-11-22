package com.dsinnovators.ei.scoring;

/** The result of a gate check (spec 7.3, 9.1 step 6). */
public enum GateOutcome {
    PASS,
    CLOSE_MISS,
    FAR_MISS,
    /** The gate has no data, or is not calculated; it has no effect on the score. */
    UNVERIFIED
}
