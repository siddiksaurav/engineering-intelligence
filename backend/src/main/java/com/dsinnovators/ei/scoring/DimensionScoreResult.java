package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * The score of one person in one dimension at one selected level.
 *
 * @param workingTier null when the person has no working tier
 * @param score 0, 1 or 2, after the gates
 * @param gates the gate results, as stored in {@code dimension_score.gates}
 * @param trail the full trail, as stored in {@code dimension_score.trail}
 */
public record DimensionScoreResult(
        long dimensionId,
        String dimensionKey,
        Level level,
        Tier workingTier,
        int score,
        List<GateResult> gates,
        DimensionTrail trail) {}
