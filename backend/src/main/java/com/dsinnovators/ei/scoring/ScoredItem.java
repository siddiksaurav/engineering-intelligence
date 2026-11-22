package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.List;
import java.util.Objects;

/**
 * A current evidence item of a person in a quarter, with what scoring needs to count it.
 *
 * @param itemKey the key of the evidence item, as shown to people
 * @param judgmentState whether the item has a tier
 * @param tier the tier of the current judgment; null unless the state is {@link JudgmentState#JUDGED}
 * @param complexity the complexity text of a Feature work item ("low" to "very_high"); null for other types
 * @param pullRequestNumbers the pull requests the item stands on
 * @param sourceUrl the link to the item's source
 */
public record ScoredItem(
        long evidenceItemId,
        String itemKey,
        EvidenceType evidenceType,
        JudgmentState judgmentState,
        Tier tier,
        String complexity,
        List<Integer> pullRequestNumbers,
        String sourceUrl) {

    public ScoredItem {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(evidenceType, "evidenceType");
        Objects.requireNonNull(judgmentState, "judgmentState");
        if ((judgmentState == JudgmentState.JUDGED) != (tier != null)) {
            throw new IllegalArgumentException("Item " + itemKey + " has tier " + tier + " in state " + judgmentState
                    + "; only a judged item has a tier");
        }
        pullRequestNumbers = List.copyOf(pullRequestNumbers);
    }

    /** True when the item has a tier and counts in presence. */
    public boolean isJudged() {
        return judgmentState == JudgmentState.JUDGED;
    }
}
