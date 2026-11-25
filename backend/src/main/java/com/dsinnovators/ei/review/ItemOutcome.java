package com.dsinnovators.ei.review;

import tools.jackson.databind.JsonNode;

/**
 * One sample item with the AI's pinned judgment, the user's label and the user's mark.
 *
 * @param labelComplexity null when the item has no label or the user could not judge it
 * @param labeled whether the item has a label at all
 * @param reasonRight null while the reason has no mark
 * @param problemMarks {@code [{name, verdict}]}; null while the reason has no mark
 */
public record ItemOutcome(
        long itemId,
        String developer,
        boolean labeled,
        String labelComplexity,
        boolean cannotJudge,
        String labelNote,
        String aiComplexity,
        String complexityByTable,
        String aiReason,
        boolean thinEvidence,
        Boolean reasonRight,
        JsonNode problemMarks,
        String markNote) {}
