package com.dsinnovators.ei.judgment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Stage 1 digest of one pull request (F06 design, "The digest"): what the model answered, checked,
 * plus {@code judgedOnPartialCode}, which the system sets and the model never sees (decision 133).
 * There is no tier of the Test change here: it comes from the test kind (decision 147).
 *
 * @param modulesTouched OpenProject modules or top-level areas
 * @param testsIncluded the model's view; the path rule decides a Test change item (decision 148)
 * @param workTypeTier set for the work types that need one, else empty
 * @param judgedOnPartialCode true when a patch was left out for size
 */
public record PullRequestDigest(
        WorkType workType,
        List<String> modulesTouched,
        boolean schemaOrDataMigration,
        boolean newIntegration,
        String natureOfChange,
        boolean testsIncluded,
        TestKind testKind,
        WorkTypeTier workTypeTier,
        SecurityKind securityKind,
        InfraKind infraKind,
        InfraScope infraScope,
        boolean dependencyChanges,
        DocumentationScope documentationScope,
        String reason,
        List<Fact> facts,
        boolean judgedOnPartialCode) {

    /** An enum whose JSON value is its lower-case name. */
    interface WireNamed {

        String name();

        default String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The 13 work types of spec 6.2. */
    public enum WorkType implements WireNamed {
        FEATURE,
        ENHANCEMENT,
        FIX,
        MAINTENANCE,
        RESEARCH,
        TECHNICAL_TASK,
        OPERATIONAL_SUPPORT,
        REFACTOR,
        TEST,
        DOCS,
        INFRA,
        PERFORMANCE,
        SECURITY;

        /** Refactor, performance, security and infra come with a tier (spec 6.4). */
        boolean needsTier() {
            return this == REFACTOR || this == PERFORMANCE || this == SECURITY || this == INFRA;
        }
    }

    public enum TestKind implements WireNamed {
        NONE,
        UNIT,
        INTEGRATION,
        REGRESSION,
        CI_GATE
    }

    public enum SecurityKind implements WireNamed {
        VULNERABILITY_FIX,
        DEPENDENCY_UPGRADE,
        ACCESS_CONTROL,
        NOT_APPLICABLE
    }

    public enum InfraKind implements WireNamed {
        PIPELINE,
        CONTAINER,
        INFRASTRUCTURE_CODE,
        MONITORING,
        RELEASE_TAG,
        RELEASE_BRANCH,
        HOTFIX,
        NOT_APPLICABLE
    }

    public enum InfraScope implements WireNamed {
        OWN_FEATURE,
        TEAM,
        NOT_APPLICABLE
    }

    public enum DocumentationScope implements WireNamed {
        OWN_WORK,
        TEAM,
        NOT_APPLICABLE
    }

    /**
     * The tier the work type gives and why.
     *
     * @param tier T1, T2 or T3; empty when the work type gives none
     */
    public record WorkTypeTier(Optional<String> tier, String reason) {}

    /**
     * One observation the digest rests on.
     *
     * @param filePath the changed file that shows it; empty text when no single file does
     */
    public record Fact(String text, String filePath) {}

    private static final String NOT_APPLICABLE = "not_applicable";

    /**
     * Reads and checks the model's answer.
     *
     * @param judgedOnPartialCode set by the system from the input, never by the model
     * @throws InvalidAnswerException naming the field and the value when the answer breaks the schema
     */
    public static PullRequestDigest fromAnswer(JsonNode answer, boolean judgedOnPartialCode) {
        if (!answer.isObject()) {
            throw new InvalidAnswerException("The answer must be a JSON object, was " + answer.getNodeType());
        }
        WorkType workType = AnswerFields.enumOf(WorkType.class, answer, "workType");
        WorkTypeTier tier = tierOf(answer.path("workTypeTier"));
        if (workType.needsTier() && tier.tier().isEmpty()) {
            throw new InvalidAnswerException("The field workTypeTier needs a tier T1 to T3 for the work type "
                    + workType.wireName() + ", was " + NOT_APPLICABLE);
        }
        return new PullRequestDigest(
                workType,
                AnswerFields.textsOf(answer, "modulesTouched"),
                AnswerFields.flagOf(answer, "schemaOrDataMigration"),
                AnswerFields.flagOf(answer, "newIntegration"),
                AnswerFields.textOf(answer, "natureOfChange"),
                AnswerFields.flagOf(answer, "testsIncluded"),
                AnswerFields.enumOf(TestKind.class, answer, "testKind"),
                tier,
                AnswerFields.enumOf(SecurityKind.class, answer, "securityKind"),
                AnswerFields.enumOf(InfraKind.class, answer, "infraKind"),
                AnswerFields.enumOf(InfraScope.class, answer, "infraScope"),
                AnswerFields.flagOf(answer, "dependencyChanges"),
                AnswerFields.enumOf(DocumentationScope.class, answer, "documentationScope"),
                AnswerFields.textOf(answer, "reason"),
                factsOf(answer),
                judgedOnPartialCode);
    }

    /** The digest as the JSON object stored in {@code judgment.value}. */
    public JsonNode toValue() {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("workType", workType.wireName());
        ArrayNode modules = value.putArray("modulesTouched");
        modulesTouched.forEach(modules::add);
        value.put("schemaOrDataMigration", schemaOrDataMigration);
        value.put("newIntegration", newIntegration);
        value.put("natureOfChange", natureOfChange);
        value.put("testsIncluded", testsIncluded);
        value.put("testKind", testKind.wireName());
        value.putObject("workTypeTier")
                .put("tier", workTypeTier.tier().orElse(NOT_APPLICABLE))
                .put("reason", workTypeTier.reason());
        value.put("securityKind", securityKind.wireName());
        value.put("infraKind", infraKind.wireName());
        value.put("infraScope", infraScope.wireName());
        value.put("dependencyChanges", dependencyChanges);
        value.put("documentationScope", documentationScope.wireName());
        value.put("reason", reason);
        ArrayNode factNodes = value.putArray("facts");
        facts.forEach(fact -> factNodes.addObject().put("fact", fact.text()).put("filePath", fact.filePath()));
        value.put("judgedOnPartialCode", judgedOnPartialCode);
        return value;
    }

    private static WorkTypeTier tierOf(JsonNode node) {
        if (!node.isObject()) {
            throw new InvalidAnswerException("The field workTypeTier must be an object, was " + node);
        }
        String tier = node.path("tier").asString();
        String reason = node.path("reason").asString();
        if (tier.equals(NOT_APPLICABLE)) {
            return new WorkTypeTier(Optional.empty(), reason);
        }
        if (!List.of("T1", "T2", "T3").contains(tier)) {
            throw new InvalidAnswerException(
                    "The field workTypeTier.tier must be T1, T2, T3 or " + NOT_APPLICABLE + ", was " + tier);
        }
        if (reason.isBlank()) {
            throw new InvalidAnswerException("The field workTypeTier.reason must not be blank for the tier " + tier);
        }
        return new WorkTypeTier(Optional.of(tier), reason);
    }

    private static List<Fact> factsOf(JsonNode answer) {
        JsonNode node = AnswerFields.required(answer, "facts");
        if (!node.isArray() || node.isEmpty()) {
            throw new InvalidAnswerException("The field facts must be a list of at least one fact, was " + node);
        }
        List<Fact> facts = new ArrayList<>();
        for (JsonNode fact : node.values()) {
            JsonNode path = fact.path("filePath");
            facts.add(new Fact(AnswerFields.textOf(fact, "fact"), path.isString() ? path.asString() : ""));
        }
        return List.copyOf(facts);
    }
}
