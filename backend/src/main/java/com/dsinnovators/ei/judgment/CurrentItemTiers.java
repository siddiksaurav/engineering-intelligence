package com.dsinnovators.ei.judgment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the current tier of evidence item rows, so that the rest of the system never sees a judgment row. */
@Service
public class CurrentItemTiers {

    private final JudgmentRepository judgments;

    CurrentItemTiers(JudgmentRepository judgments) {
        this.judgments = judgments;
    }

    /**
     * The current {@code item_tier} judgment of each of the item rows, keyed by item row id. An item
     * without a tier is not in the map. One query for all items.
     */
    @Transactional(readOnly = true)
    public Map<Long, ItemTier> ofItems(Collection<Long> evidenceItemIds) {
        if (evidenceItemIds.isEmpty()) {
            return Map.of();
        }
        List<Judgment> current = judgments.findCurrentOfEvidenceItems(JudgmentKind.ITEM_TIER, evidenceItemIds);
        return current.stream()
                .collect(Collectors.toMap(
                        Judgment::getEvidenceItemId,
                        judgment -> new ItemTier(judgment.getTier(), judgment.getReason())));
    }
}
