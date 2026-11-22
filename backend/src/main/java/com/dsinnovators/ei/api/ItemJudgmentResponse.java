package com.dsinnovators.ei.api;

import com.dsinnovators.ei.judgment.ItemJudgment;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * The current judgment of an item. The other fields are null unless the state is {@code judged}.
 *
 * @param state {@code judged}, {@code not_judged_yet} or {@code not_tiered} (the type has no judgment, decision 241)
 * @param facts {@code [{fact, link}]}
 */
record ItemJudgmentResponse(
        String state,
        String kind,
        String tier,
        JsonNode value,
        String reason,
        JsonNode facts,
        String model,
        String promptVersion,
        Instant judgedAt) {

    private static final ItemJudgmentResponse NOT_TIERED = withoutJudgment("not_tiered");
    private static final ItemJudgmentResponse NOT_JUDGED_YET = withoutJudgment("not_judged_yet");

    static ItemJudgmentResponse notTiered() {
        return NOT_TIERED;
    }

    static ItemJudgmentResponse notJudgedYet() {
        return NOT_JUDGED_YET;
    }

    static ItemJudgmentResponse judged(ItemJudgment judgment) {
        return new ItemJudgmentResponse(
                "judged",
                judgment.kind().databaseName(),
                judgment.tier(),
                judgment.value(),
                judgment.reason(),
                judgment.facts(),
                judgment.model(),
                judgment.promptVersion(),
                judgment.judgedAt());
    }

    private static ItemJudgmentResponse withoutJudgment(String state) {
        return new ItemJudgmentResponse(state, null, null, null, null, null, null, null, null);
    }
}
