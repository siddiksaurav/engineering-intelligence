package com.dsinnovators.ei.review;

import java.util.List;
import java.util.Map;

/**
 * The numbers of the blind check (F08 design, flow 7).
 *
 * @param labeled items with a label, {@code cannot_judge} included
 * @param cannotJudge items the user could not label; they count in no share
 * @param reasonMarked items whose reason has a mark
 * @param exactMatch the user's complexity equals the AI's, over the items labeled with a complexity
 * @param withinOneStep the two are at most one place apart in low, medium, high, very_high
 * @param tableExactMatch as {@code exactMatch}, against the complexity the table gives
 * @param reasonRight reasons marked right, over the items marked
 * @param confusion the count per pair of the user's label and the AI's complexity; empty pairs are left out
 * @param problemVerdicts the count per verdict over all marked problems
 */
public record BlindCheckSummary(
        int sampled,
        int labeled,
        int cannotJudge,
        int reasonMarked,
        Rate exactMatch,
        Rate withinOneStep,
        Rate tableExactMatch,
        Rate reasonRight,
        List<ConfusionCell> confusion,
        Map<String, Integer> problemVerdicts,
        ThinEvidence thinEvidence,
        Targets targets) {}
