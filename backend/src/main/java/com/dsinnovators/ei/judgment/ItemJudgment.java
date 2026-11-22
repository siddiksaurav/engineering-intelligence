package com.dsinnovators.ei.judgment;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * The current judgment of one evidence item row, as the rest of the system may read it: the
 * judgment row itself stays inside this package.
 *
 * @param tier the tier, when the kind gives one; else null
 * @param value the model's own value, in the shape of the kind
 * @param facts the facts as {@code [{fact, link}]}
 * @param confidence the model's own value from 0 to 1, when the kind has one; else null
 */
public record ItemJudgment(
        JudgmentKind kind,
        String tier,
        JsonNode value,
        String reason,
        JsonNode facts,
        JudgedBy judgedBy,
        String model,
        String promptVersion,
        Double confidence,
        Instant judgedAt) {

    static ItemJudgment of(Judgment judgment) {
        return new ItemJudgment(
                judgment.getKind(),
                judgment.getTier(),
                judgment.getValue(),
                judgment.getReason(),
                judgment.getFactsAsJson(),
                judgment.getJudgedBy(),
                judgment.getModel(),
                judgment.getPromptVersion(),
                judgment.getConfidence(),
                judgment.getJudgedAt());
    }
}
