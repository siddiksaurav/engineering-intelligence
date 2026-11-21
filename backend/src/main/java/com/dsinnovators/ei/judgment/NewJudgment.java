package com.dsinnovators.ei.judgment;

import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * A judgment before it is stored. It is checked when it is made, so a judgment without a reason, a
 * fact with a link, or, for a model, the model and the prompt version never reaches the database.
 *
 * @param value the answer: the digest, or the facts an item tier rests on
 * @param tier T1 to T4 when the kind gives one, else null
 * @param facts at least one, each with a link
 * @param confidence the model's own value from 0 to 1 when the kind has one, else null
 * @param model the model id the provider answered with; required for a judgment by a model
 * @param promptVersion such as {@code pull-request-digest/v1}; required for a judgment by a model
 * @param inputHash SHA-256 of the input; the same input is never judged twice by a model
 * @param basedOnJudgmentId for an item tier, the digest it was read from; else null
 * @param supersedesId the judgment this one replaces, when it is not the current one of the same
 *     subject, such as the digest of an older version of the same pull request; else null
 */
public record NewJudgment(
        JudgmentKind kind,
        JudgmentSubject subject,
        JsonNode value,
        String tier,
        String reason,
        List<JudgmentFact> facts,
        Double confidence,
        JudgedBy judgedBy,
        String model,
        String promptVersion,
        String inputHash,
        Long basedOnJudgmentId,
        Long supersedesId) {

    private static final Pattern TIER = Pattern.compile("T[1-4]");

    public NewJudgment {
        require(
                kind != null && subject != null && value != null && judgedBy != null,
                "kind, subject, value and judgedBy");
        require(subject.type() == kind.subjectType(), "a subject of the type " + kind.subjectType() + " for " + kind);
        require(tier == null || TIER.matcher(tier).matches(), "a tier from T1 to T4, was " + tier);
        require(!isBlank(reason), "a reason");
        require(facts != null && !facts.isEmpty(), "at least one fact");
        require(
                confidence == null || (confidence >= 0 && confidence <= 1),
                "a confidence from 0 to 1, was " + confidence);
        require(!isBlank(inputHash), "an input hash");
        require(
                judgedBy != JudgedBy.MODEL || (!isBlank(model) && !isBlank(promptVersion)),
                "the model and the prompt version of a judgment by a model");
        facts = List.copyOf(facts);
    }

    private static void require(boolean condition, String needed) {
        if (!condition) {
            throw new IllegalArgumentException("A judgment needs " + needed);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
