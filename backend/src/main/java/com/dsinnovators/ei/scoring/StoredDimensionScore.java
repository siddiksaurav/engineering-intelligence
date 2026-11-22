package com.dsinnovators.ei.scoring;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * One stored dimension score as it is shown.
 *
 * @param workingTier null when the person has no working tier
 * @param gates the stored gate results
 * @param notes the notes of the score, copied from the trail
 * @param trail the stored trail
 */
public record StoredDimensionScore(
        String dimensionKey,
        String dimensionName,
        Tier workingTier,
        int score,
        JsonNode gates,
        List<String> notes,
        JsonNode trail) {}
