package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.Map;
import java.util.Set;

/**
 * The {@code scoring.*} settings in force for a quarter (spec 14), read once per run.
 *
 * @param presenceThreshold the share of a frequent type that gives presence at a tier
 * @param featureWorkWeights the weight of a Feature work item by tier
 * @param frequentEvidenceTypes the types that have a presence share; the others need one item
 * @param t3ItemsNeededAtLevel3 by dimension key, the T3 items needed at level L3
 * @param gateLimits the limits of each gate with a comparison
 * @param gateScored by gate with limits, whether its result may lower a dimension score (decision 315)
 * @param gateMinimumDevelopers the fewest developers a gate group needs
 * @param gateMinimumPullRequestsPerDeveloper the fewest pull requests of a gate's kind that make a developer count
 */
public record ScoringSettings(
        PresenceThreshold presenceThreshold,
        Map<Tier, Integer> featureWorkWeights,
        Set<EvidenceType> frequentEvidenceTypes,
        Map<String, Integer> t3ItemsNeededAtLevel3,
        Map<Gate, GateLimits> gateLimits,
        Map<Gate, Boolean> gateScored,
        int gateMinimumDevelopers,
        int gateMinimumPullRequestsPerDeveloper) {

    public ScoringSettings {
        featureWorkWeights = Map.copyOf(featureWorkWeights);
        frequentEvidenceTypes = Set.copyOf(frequentEvidenceTypes);
        t3ItemsNeededAtLevel3 = Map.copyOf(t3ItemsNeededAtLevel3);
        gateLimits = Map.copyOf(gateLimits);
        gateScored = Map.copyOf(gateScored);
    }
}
