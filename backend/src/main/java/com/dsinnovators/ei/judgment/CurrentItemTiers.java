package com.dsinnovators.ei.judgment;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Reads the current tier of evidence item rows, so that the rest of the system never sees a judgment row. */
@Service
public class CurrentItemTiers {

    private final CurrentItemJudgments currentJudgments;

    CurrentItemTiers(CurrentItemJudgments currentJudgments) {
        this.currentJudgments = currentJudgments;
    }

    /**
     * The current {@code item_tier} judgment of each of the item rows, keyed by item row id. An item
     * without a tier is not in the map. One query for all items.
     */
    public Map<Long, ItemTier> ofItems(Collection<Long> evidenceItemIds) {
        return currentJudgments.ofItems(JudgmentKind.ITEM_TIER, evidenceItemIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ItemTier(
                                entry.getValue().tier(), entry.getValue().reason())));
    }
}
