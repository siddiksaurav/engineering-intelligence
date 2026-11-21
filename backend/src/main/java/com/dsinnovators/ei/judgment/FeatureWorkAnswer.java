package com.dsinnovators.ei.judgment;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The Stage 2 answer for one Feature work item (F07 design, "The answer"), read and checked. The tier
 * is not part of it: it is read from the complexity table (spec 6.2). Used by the Feature work judge,
 * a later task of F07.
 *
 * @param workType the one work type of the item, separate from the work types of its pull requests
 * @param facts the observations the complexity rests on, at least one
 * @param confidence the model's own value from 0 to 1 (decision 173)
 */
record FeatureWorkAnswer(
        PullRequestDigest.WorkType workType,
        Complexity complexity,
        Rubric rubric,
        List<Fact> facts,
        String reason,
        double confidence) {

    /** The four complexities of spec 6.2. */
    enum Complexity implements PullRequestDigest.WireNamed {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    enum DataChange implements PullRequestDigest.WireNamed {
        NONE,
        SCHEMA_CHANGE,
        PRODUCTION_DATA_MIGRATION
    }

    enum Integration implements PullRequestDigest.WireNamed {
        NONE,
        EXISTING_INTEGRATION,
        NEW_INTEGRATION
    }

    /** The facts of spec 6.2 that the complexity rests on; the module count is one of them, never the rule. */
    record Rubric(
            List<String> modulesTouched,
            DataChange dataChange,
            Integration integration,
            boolean requirementsUnclear,
            boolean knownPattern,
            boolean acrossSystems,
            boolean newArchitecture,
            boolean clientCriticalWithoutEarlierExample) {}

    /**
     * One observation of the answer.
     *
     * @param quote text copied from the input that shows the fact
     * @param source the key of the input part the fact comes from, such as WI-81224 or PR-24204
     */
    record Fact(String text, String quote, String source) {}

    /**
     * Reads and checks the model's answer.
     *
     * @throws InvalidAnswerException naming the field and the value when the answer breaks the schema
     */
    static FeatureWorkAnswer fromAnswer(JsonNode answer) {
        if (!answer.isObject()) {
            throw new InvalidAnswerException("The answer must be a JSON object, was " + answer.getNodeType());
        }
        return new FeatureWorkAnswer(
                AnswerFields.enumOf(PullRequestDigest.WorkType.class, answer, "workType"),
                AnswerFields.enumOf(Complexity.class, answer, "complexity"),
                rubricOf(AnswerFields.objectOf(answer, "rubric")),
                factsOf(answer),
                AnswerFields.textOf(answer, "reason"),
                confidenceOf(answer));
    }

    private static Rubric rubricOf(JsonNode rubric) {
        return new Rubric(
                AnswerFields.textsOf(rubric, "modulesTouched"),
                AnswerFields.enumOf(DataChange.class, rubric, "dataChange"),
                AnswerFields.enumOf(Integration.class, rubric, "integration"),
                AnswerFields.flagOf(rubric, "requirementsUnclear"),
                AnswerFields.flagOf(rubric, "knownPattern"),
                AnswerFields.flagOf(rubric, "acrossSystems"),
                AnswerFields.flagOf(rubric, "newArchitecture"),
                AnswerFields.flagOf(rubric, "clientCriticalWithoutEarlierExample"));
    }

    private static List<Fact> factsOf(JsonNode answer) {
        JsonNode node = AnswerFields.required(answer, "facts");
        if (!node.isArray() || node.isEmpty()) {
            throw new InvalidAnswerException("The field facts must be a list of at least one fact, was " + node);
        }
        List<Fact> facts = new ArrayList<>();
        for (JsonNode fact : node.values()) {
            facts.add(new Fact(
                    AnswerFields.textOf(fact, "fact"),
                    AnswerFields.textOrEmptyOf(fact, "quote"),
                    AnswerFields.textOrEmptyOf(fact, "source")));
        }
        return List.copyOf(facts);
    }

    private static double confidenceOf(JsonNode answer) {
        JsonNode node = AnswerFields.required(answer, "confidence");
        if (!node.isNumber() || node.doubleValue() < 0 || node.doubleValue() > 1) {
            throw new InvalidAnswerException("The field confidence must be a number from 0 to 1, was " + node);
        }
        return node.doubleValue();
    }
}
