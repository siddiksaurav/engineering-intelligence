package com.dsinnovators.ei.scoring;

import java.time.Instant;
import java.util.List;

/**
 * The stored scores of one person at one level from one run.
 *
 * @param jobRunId null when nothing is stored for the person
 * @param calculatedAt null when nothing is stored for the person
 */
public record StoredDimensionScores(Long jobRunId, Instant calculatedAt, List<StoredDimensionScore> dimensions) {

    private static final StoredDimensionScores NOTHING = new StoredDimensionScores(null, null, List.of());

    /** The answer when no run has scored the person in the quarter. */
    public static StoredDimensionScores nothing() {
        return NOTHING;
    }

    /** True when at least one dimension score is stored. */
    public boolean scored() {
        return !dimensions.isEmpty();
    }
}
