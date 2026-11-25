package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.BlindCheckSummary;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * The numbers of the blind check and the items behind them, with the titles the page shows.
 *
 * @param reasonsToMark items labeled with a complexity whose reason has no mark yet
 * @param disagreements items where the complexity differs or the reason is marked wrong
 * @param cannotJudgeItems items the user could not label
 */
record BlindCheckResponse(
        @JsonUnwrapped BlindCheckSummary summary,
        List<ReasonToMark> reasonsToMark,
        List<Disagreement> disagreements,
        List<CannotJudgeItem> cannotJudgeItems) {

    record ReasonToMark(long itemId, String title) {}

    /**
     * @param userComplexity null when the item has no label or the user could not judge it
     * @param reasonRight null while the reason has no mark
     */
    record Disagreement(
            long itemId,
            String title,
            String developer,
            String userComplexity,
            String aiComplexity,
            String complexityByTable,
            String userNote,
            String aiReason,
            Boolean reasonRight,
            String markNote) {}

    record CannotJudgeItem(long itemId, String title, String note) {}
}
