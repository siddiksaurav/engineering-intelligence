package com.dsinnovators.ei.scoring;

import java.util.Optional;

/** Checks gate G1 or G2 of a person against the repository (spec 7.3, decisions 209, 222, 223, 224). */
public final class GateCheck {

    private final ScoringSettings settings;

    public GateCheck(ScoringSettings settings) {
        this.settings = settings;
    }

    public GateResult check(Gate gate, GateNumbers numbers) {
        GateLimits limits = Optional.ofNullable(settings.gateLimits().get(gate))
                .orElseThrow(() -> new IllegalStateException("No gate limits in the settings for gate " + gate));
        boolean scored = Optional.ofNullable(settings.gateScored().get(gate))
                .orElseThrow(() -> new IllegalStateException("No scoring switch in the settings for gate " + gate));
        UnverifiedReason reason = unverifiedReason(numbers);
        if (reason != null) {
            return result(gate, scored, limits, numbers, GateOutcome.UNVERIFIED, reason);
        }
        return result(gate, scored, limits, numbers, outcomeOf(numbers, limits), null);
    }

    private UnverifiedReason unverifiedReason(GateNumbers numbers) {
        if (numbers.developersInGroup().size() < settings.gateMinimumDevelopers()) {
            return UnverifiedReason.TOO_FEW_DEVELOPERS;
        }
        if (numbers.personTotal() == 0) {
            return UnverifiedReason.NO_PULL_REQUEST_OF_THE_KIND;
        }
        if (numbers.repositoryTotal() == 0) {
            return UnverifiedReason.NO_REPOSITORY_PULL_REQUESTS;
        }
        return null;
    }

    /**
     * The difference in points is (repositoryTested / repositoryTotal - personTested / personTotal) * 100. It is
     * compared with the limits as a fraction in whole numbers, so a difference of exactly 10 points is not rounded
     * either way.
     */
    private static GateOutcome outcomeOf(GateNumbers numbers, GateLimits limits) {
        long differenceNumerator = Math.subtractExact(
                Math.multiplyExact(numbers.repositoryTested(), numbers.personTotal()),
                Math.multiplyExact(numbers.personTested(), numbers.repositoryTotal()));
        long denominator = Math.multiplyExact(numbers.repositoryTotal(), numbers.personTotal());
        long points = Math.multiplyExact(differenceNumerator, 100);
        if (points <= Math.multiplyExact((long) limits.passWithinPoints(), denominator)) {
            return GateOutcome.PASS;
        }
        if (points <= Math.multiplyExact((long) limits.farMissBeyondPoints(), denominator)) {
            return GateOutcome.CLOSE_MISS;
        }
        return GateOutcome.FAR_MISS;
    }

    private GateResult result(
            Gate gate,
            boolean scored,
            GateLimits limits,
            GateNumbers numbers,
            GateOutcome outcome,
            UnverifiedReason reason) {
        Double personShare = sharePercent(numbers.personTested(), numbers.personTotal());
        Double repositoryShare = sharePercent(numbers.repositoryTested(), numbers.repositoryTotal());
        Double difference = personShare == null || repositoryShare == null ? null : repositoryShare - personShare;
        return new GateResult(
                gate,
                outcome,
                scored,
                reason,
                numbers.personTestedPullRequests(),
                numbers.personPullRequests(),
                numbers.repositoryTestedPullRequests(),
                numbers.repositoryPullRequests(),
                personShare,
                repositoryShare,
                difference,
                numbers.developersInGroup(),
                settings.gateMinimumDevelopers(),
                limits,
                numbers.pullRequestsWithoutDigest(),
                scored ? ScoreNotes.forGate(gate, outcome) : ScoreNotes.notScoredYet(gate));
    }

    private static Double sharePercent(long tested, long total) {
        return total == 0 ? null : (double) tested * 100 / total;
    }
}
