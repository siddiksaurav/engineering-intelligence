package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * One gate checked for a person, with every number the result rests on.
 *
 * @param unverifiedReason null unless the outcome is {@link GateOutcome#UNVERIFIED}
 * @param personSharePercent null when the person has no pull request of the kind or the gate is not calculated
 * @param repositorySharePercent null when the repository has no pull request of the kind or the gate is not calculated
 * @param differencePoints the repository share minus the person's, in points; negative when the person is above;
 *     null when a share is missing
 * @param limits the limits used; null when the gate is not calculated
 * @param note the note for the trail; null when the gate passes
 */
public record GateResult(
        Gate gate,
        GateOutcome outcome,
        UnverifiedReason unverifiedReason,
        List<Integer> personTestedPullRequests,
        List<Integer> personPullRequests,
        long repositoryTested,
        long repositoryTotal,
        Double personSharePercent,
        Double repositorySharePercent,
        Double differencePoints,
        int developersInGroup,
        int minimumDevelopers,
        GateLimits limits,
        long pullRequestsWithoutDigest,
        String note) {

    /** The result of a gate on the dimension that is never calculated (G4, decision 210). */
    static GateResult notCalculated(Gate gate) {
        return new GateResult(
                gate,
                GateOutcome.UNVERIFIED,
                UnverifiedReason.NOT_CALCULATED,
                List.of(),
                List.of(),
                0,
                0,
                null,
                null,
                null,
                0,
                0,
                null,
                0,
                ScoreNotes.forGate(gate, GateOutcome.UNVERIFIED));
    }
}
