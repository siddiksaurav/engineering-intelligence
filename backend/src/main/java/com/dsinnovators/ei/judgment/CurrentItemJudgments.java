package com.dsinnovators.ei.judgment;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the current judgment of evidence item rows, so that the rest of the system never sees a judgment row. */
@Service
public class CurrentItemJudgments {

    private final JudgmentRepository judgments;

    CurrentItemJudgments(JudgmentRepository judgments) {
        this.judgments = judgments;
    }

    /**
     * The current judgment of the kind of each of the item rows, keyed by item row id. An item without
     * one is not in the map. One query for all items.
     */
    @Transactional(readOnly = true)
    public Map<Long, ItemJudgment> ofItems(JudgmentKind kind, Collection<Long> evidenceItemIds) {
        if (evidenceItemIds.isEmpty()) {
            return Map.of();
        }
        return judgments.findCurrentOfEvidenceItems(kind, evidenceItemIds).stream()
                .collect(Collectors.toMap(Judgment::getEvidenceItemId, ItemJudgment::of));
    }
}
