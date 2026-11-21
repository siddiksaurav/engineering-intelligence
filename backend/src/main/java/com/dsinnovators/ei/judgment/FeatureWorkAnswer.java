package com.dsinnovators.ei.judgment;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Stage 2 answer for one Feature work item (F07 design, "The answer"), read and checked. The tier
 * is not part of it: it is read from the complexity table (spec 6.2).
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

    /**
     * The answer as the JSON object stored in {@code judgment.value}, with what the system knows about the input.
     *
     * @param leftOutPullRequests the external ids of the pull requests left out for size
     */
    JsonNode toValue(boolean judgedOnPartialInput, List<String> leftOutPullRequests) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("workType", workType.wireName());
        value.put("complexity", complexity.wireName());
        ObjectNode rubricNode = value.putObject("rubric");
        ArrayNode modules = rubricNode.putArray("modulesTouched");
        rubric.modulesTouched().forEach(modules::add);
        rubricNode.put("dataChange", rubric.dataChange().wireName());
        rubricNode.put("integration", rubric.integration().wireName());
        rubricNode.put("requirementsUnclear", rubric.requirementsUnclear());
        rubricNode.put("knownPattern", rubric.knownPattern());
        rubricNode.put("acrossSystems", rubric.acrossSystems());
        rubricNode.put("newArchitecture", rubric.newArchitecture());
        rubricNode.put("clientCriticalWithoutEarlierExample", rubric.clientCriticalWithoutEarlierExample());
        ArrayNode factNodes = value.putArray("facts");
        facts.forEach(fact -> factNodes
                .addObject()
                .put("fact", fact.text())
                .put("quote", fact.quote())
                .put("source", fact.source()));
        value.put("reason", reason);
        value.put("confidence", confidence);
        value.put("judgedOnPartialInput", judgedOnPartialInput);
        ArrayNode leftOut = value.putArray("leftOutPullRequests");
        leftOutPullRequests.forEach(leftOut::add);
        return value;
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
