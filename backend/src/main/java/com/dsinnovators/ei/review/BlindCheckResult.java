package com.dsinnovators.ei.review;

import java.util.List;

/**
 * The numbers and the items behind them, in the order of the sample.
 *
 * @param reasonsToMark items labeled with a complexity whose reason has no mark yet
 * @param disagreements items where the complexity differs or the reason is marked wrong
 * @param cannotJudgeItems items the user could not label
 */
public record BlindCheckResult(
        BlindCheckSummary summary,
        List<ItemOutcome> reasonsToMark,
        List<ItemOutcome> disagreements,
        List<ItemOutcome> cannotJudgeItems) {}
