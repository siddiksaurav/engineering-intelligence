package com.dsinnovators.ei.judgment;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Stage 2 answer for one Feature work item (F16 design, "The prompt"), read and checked: the
 * problems the work solved with the depth of each, three flags, and the complexity the model chose.
 * The tier is not part of it: it is read from the complexity table (spec 6.2).
 *
 * @param workType the one work type of the item, separate from the work types of its pull requests
 * @param problems the problems the work solved, at least one
 * @param problemsAffectEachOther one yes or no for the whole item (decision 324)
 * @param mistakesStayHidden true for a design whose mistakes stay hidden across the system
 * @param reachesAcrossProjects true when the problems reach across projects or the organization
 * @param thinEvidence true when the model could not really see the problems (decision 321)
 * @param complexity the model's own choice; the table of spec 6.2 is guidance (decision 323)
 * @param confidence the model's own value from 0 to 1 (decision 173)
 */
record FeatureWorkAnswer(
        PullRequestDigest.WorkType workType,
        List<Problem> problems,
        boolean problemsAffectEachOther,
        String problemsAffectEachOtherReason,
        boolean mistakesStayHidden,
        String mistakesStayHiddenReason,
        boolean reachesAcrossProjects,
        boolean thinEvidence,
        Complexity complexity,
        String reason,
        double confidence) {

    /** The earlier example of a problem is not searched in the PoC (decision 303). */
    private static final String EARLIER_EXAMPLE_NOT_CHECKED = "not_checked";

    /** The four complexities of spec 6.2. */
    enum Complexity implements WireNamed {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    /** The depth of a problem (spec 6.2, Move 2). */
    enum Depth implements WireNamed {
        ROUTINE,
        ANSWER_EXISTS,
        NO_READY_ANSWER
    }

    /**
     * One problem the work solved.
     *
     * @param quote text copied from the input that shows the problem; may be empty
     * @param source the key of the input part the quote comes from, such as WI-81224 or PR-24204
     */
    record Problem(String name, Depth depth, String depthReason, String quote, String source) {}

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
                problemsOf(answer),
                AnswerFields.flagOf(answer, "problemsAffectEachOther"),
                AnswerFields.textOrEmptyOf(answer, "problemsAffectEachOtherReason"),
                AnswerFields.flagOf(answer, "mistakesStayHidden"),
                AnswerFields.textOrEmptyOf(answer, "mistakesStayHiddenReason"),
                AnswerFields.flagOf(answer, "reachesAcrossProjects"),
                AnswerFields.flagOf(answer, "thinEvidence"),
                AnswerFields.enumOf(Complexity.class, answer, "complexity"),
                AnswerFields.textOf(answer, "reason"),
                confidenceOf(answer));
    }

    /** What the table of spec 6.2 gives for the depths and flags of this answer. */
    Complexity complexityByTable() {
        return ComplexityTable.complexityOf(
                problems.stream().map(Problem::depth).toList(),
                problemsAffectEachOther,
                mistakesStayHidden,
                reachesAcrossProjects);
    }

    /**
     * The answer as the JSON object stored in {@code judgment.value}, with what the system knows about the input.
     *
     * @param leftOutPullRequests the external ids of the pull requests left out for size
     */
    JsonNode toValue(boolean judgedOnPartialInput, List<String> leftOutPullRequests) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("workType", workType.wireName());
        ArrayNode problemNodes = value.putArray("problems");
        problems.forEach(problem -> problemNodes
                .addObject()
                .put("name", problem.name())
                .put("depth", problem.depth().wireName())
                .put("depthReason", problem.depthReason())
                .put("quote", problem.quote())
                .put("source", problem.source())
                .put("earlierExample", EARLIER_EXAMPLE_NOT_CHECKED));
        value.put("problemsAffectEachOther", problemsAffectEachOther);
        value.put("problemsAffectEachOtherReason", problemsAffectEachOtherReason);
        value.put("mistakesStayHidden", mistakesStayHidden);
        value.put("mistakesStayHiddenReason", mistakesStayHiddenReason);
        value.put("reachesAcrossProjects", reachesAcrossProjects);
        value.put("thinEvidence", thinEvidence);
        value.put("complexity", complexity.wireName());
        value.put("complexityByTable", complexityByTable().wireName());
        value.put("reason", reason);
        value.put("judgedOnPartialInput", judgedOnPartialInput);
        ArrayNode leftOut = value.putArray("leftOutPullRequests");
        leftOutPullRequests.forEach(leftOut::add);
        return value;
    }

    private static List<Problem> problemsOf(JsonNode answer) {
        JsonNode node = AnswerFields.required(answer, "problems");
        if (!node.isArray() || node.isEmpty()) {
            throw new InvalidAnswerException("The field problems must be a list of at least one problem, was " + node);
        }
        List<Problem> problems = new ArrayList<>();
        for (JsonNode problem : node.values()) {
            problems.add(new Problem(
                    AnswerFields.textOf(problem, "name"),
                    AnswerFields.enumOf(Depth.class, problem, "depth"),
                    AnswerFields.textOf(problem, "depthReason"),
                    AnswerFields.textOrEmptyOf(problem, "quote"),
                    AnswerFields.textOrEmptyOf(problem, "source")));
        }
        return List.copyOf(problems);
    }

    private static double confidenceOf(JsonNode answer) {
        JsonNode node = AnswerFields.required(answer, "confidence");
        if (!node.isNumber() || node.doubleValue() < 0 || node.doubleValue() > 1) {
            throw new InvalidAnswerException("The field confidence must be a number from 0 to 1, was " + node);
        }
        return node.doubleValue();
    }
}
