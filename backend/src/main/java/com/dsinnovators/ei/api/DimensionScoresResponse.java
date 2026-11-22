package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.scoring.Level;
import com.dsinnovators.ei.scoring.StoredDimensionScore;
import com.dsinnovators.ei.scoring.StoredDimensionScores;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The dimension scores of a developer for a quarter at a selected level. {@code scored} is false,
 * with no run id and no dimensions, when no run has scored the developer in the quarter.
 */
record DimensionScoresResponse(
        PersonResponse person,
        String quarter,
        Level level,
        boolean scored,
        Long jobRunId,
        Instant calculatedAt,
        List<DimensionScoreResponse> dimensions) {

    record DimensionScoreResponse(
            String key,
            String name,
            String workingTier,
            int score,
            JsonNode gates,
            List<String> notes,
            JsonNode trail) {

        static DimensionScoreResponse of(StoredDimensionScore stored) {
            return new DimensionScoreResponse(
                    stored.dimensionKey(),
                    stored.dimensionName(),
                    stored.workingTier() == null ? null : stored.workingTier().name(),
                    stored.score(),
                    stored.gates(),
                    stored.notes(),
                    stored.trail());
        }
    }

    static DimensionScoresResponse of(
            PersonResponse person, Quarter quarter, Level level, StoredDimensionScores stored) {
        return new DimensionScoresResponse(
                person,
                quarter.toString(),
                level,
                stored.scored(),
                stored.jobRunId(),
                stored.calculatedAt(),
                stored.dimensions().stream().map(DimensionScoreResponse::of).toList());
    }
}
