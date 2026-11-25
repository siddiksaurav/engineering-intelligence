package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * A model for tests that never calls a real one (decision 142). It answers a valid digest of a fix
 * unless a reply was scripted or another default set, and it keeps every request it was given.
 */
final class FakeJudgmentModel implements JudgmentModel {

    static final String MODEL = "fake-model-1";
    static final long INPUT_TOKENS = 100;
    static final long OUTPUT_TOKENS = 20;

    private final Deque<Supplier<ModelAnswer>> scripted = new ArrayDeque<>();
    private final List<ModelRequest> requests = new ArrayList<>();
    private final List<ModelRequest> refusedRequests = new ArrayList<>();
    private final List<String> refusalReasons = new ArrayList<>();
    private JsonNode unscriptedAnswer = aDigest();

    /** A digest answer that follows the schema, for a fix with a regression spec. */
    static ObjectNode aDigest() {
        return (ObjectNode) CanonicalJson.parse("""
                {
                  "workType": "fix",
                  "modulesTouched": ["core"],
                  "schemaOrDataMigration": false,
                  "newIntegration": false,
                  "natureOfChange": "Stops the list from crashing on an empty date.",
                  "testsIncluded": true,
                  "testKind": "regression",
                  "workTypeTier": {"tier": "not_applicable", "reason": ""},
                  "securityKind": "not_applicable",
                  "infraKind": "not_applicable",
                  "infraScope": "not_applicable",
                  "dependencyChanges": false,
                  "documentationScope": "not_applicable",
                  "reason": "The title names a crash that is now handled.",
                  "facts": [{"fact": "Adds a regression spec", "filePath": "spec/list_spec.rb"}]
                }
                """);
    }

    /** An answer for a Feature work item that follows the schema; the complexity is the one given. */
    static ObjectNode aFeatureWork(String complexity) {
        return (ObjectNode) CanonicalJson.parse("""
                {
                  "workType": "feature",
                  "problems": [
                    {
                      "name": "Store the series",
                      "depth": "answer_exists",
                      "depthReason": "A known pattern covers it and the project had no example.",
                      "quote": "Nature of the change",
                      "source": "PR-1"
                    },
                    {
                      "name": "Alert on a missed series",
                      "depth": "routine",
                      "depthReason": "A standard notification.",
                      "quote": "Users miss alerts",
                      "source": "WI-100"
                    }
                  ],
                  "problemsAffectEachOther": false,
                  "problemsAffectEachOtherReason": "",
                  "mistakesStayHidden": false,
                  "mistakesStayHiddenReason": "",
                  "reachesAcrossProjects": false,
                  "thinEvidence": false,
                  "complexity": "%s",
                  "reason": "One problem has a known answer and no earlier example.",
                  "confidence": 0.8
                }
                """.formatted(complexity));
    }

    /** An answer for review comments that follows the schema; the arguments are pairs of a comment id and a type. */
    static ObjectNode aReviewComments(String... commentIdsAndTypes) {
        ObjectNode answer = JsonNodeFactory.instance.objectNode();
        ArrayNode comments = answer.putArray("comments");
        for (int index = 0; index < commentIdsAndTypes.length; index += 2) {
            comments.addObject()
                    .put("commentId", commentIdsAndTypes[index])
                    .put("commentType", commentIdsAndTypes[index + 1])
                    .put("reason", "Reason for " + commentIdsAndTypes[index]);
        }
        return answer;
    }

    static ModelAnswer answerOf(JsonNode digest) {
        return new ModelAnswer(digest, MODEL, INPUT_TOKENS, OUTPUT_TOKENS);
    }

    /** Every call that no script covers answers this instead of a digest. */
    FakeJudgmentModel answeringByDefault(JsonNode answer) {
        unscriptedAnswer = answer;
        return this;
    }

    /** The next call answers this. */
    FakeJudgmentModel thenAnswers(JsonNode digest) {
        scripted.add(() -> answerOf(digest));
        return this;
    }

    /** The next call fails with this. */
    FakeJudgmentModel thenFails(RuntimeException failure) {
        scripted.add(() -> {
            throw failure;
        });
        return this;
    }

    /** The next call runs this, such as a wait, and answers a valid digest. */
    FakeJudgmentModel thenDoes(Runnable action) {
        scripted.add(() -> {
            action.run();
            return answerOf(unscriptedAnswer);
        });
        return this;
    }

    int calls() {
        return requests.size();
    }

    List<ModelRequest> requests() {
        return List.copyOf(requests);
    }

    List<ModelRequest> refusedRequests() {
        return List.copyOf(refusedRequests);
    }

    List<String> refusalReasons() {
        return List.copyOf(refusalReasons);
    }

    @Override
    public void answerRefused(ModelRequest request, String reason) {
        refusedRequests.add(request);
        refusalReasons.add(reason);
    }

    @Override
    public ModelAnswer judge(ModelRequest request) {
        requests.add(request);
        Supplier<ModelAnswer> next = scripted.poll();
        return next == null ? answerOf(unscriptedAnswer) : next.get();
    }
}
