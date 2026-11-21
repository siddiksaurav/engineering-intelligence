package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A model for tests that never calls a real one (decision 142). It answers a valid digest of a fix
 * unless a reply was scripted, and it keeps every request it was given.
 */
final class FakeJudgmentModel implements JudgmentModel {

    static final String MODEL = "fake-model-1";
    static final long INPUT_TOKENS = 100;
    static final long OUTPUT_TOKENS = 20;

    private final Deque<Supplier<ModelAnswer>> scripted = new ArrayDeque<>();
    private final List<ModelRequest> requests = new ArrayList<>();

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

    static ModelAnswer answerOf(JsonNode digest) {
        return new ModelAnswer(digest, MODEL, INPUT_TOKENS, OUTPUT_TOKENS);
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
            return answerOf(aDigest());
        });
        return this;
    }

    int calls() {
        return requests.size();
    }

    List<ModelRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public ModelAnswer judge(ModelRequest request) {
        requests.add(request);
        Supplier<ModelAnswer> next = scripted.poll();
        return next == null ? answerOf(aDigest()) : next.get();
    }
}
