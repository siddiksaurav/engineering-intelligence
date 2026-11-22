package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * An evidence item as the trail shows it.
 *
 * @param weight what the item adds to a presence sum: the tier weight for Feature work, else one; null when the
 *     item has no tier
 */
public record TrailItem(
        long evidenceItemId,
        String itemKey,
        String evidenceType,
        String tier,
        String complexity,
        Integer weight,
        List<Integer> pullRequestNumbers,
        String sourceUrl) {

    static TrailItem of(ScoredItem item, Integer weight) {
        return new TrailItem(
                item.evidenceItemId(),
                item.itemKey(),
                item.evidenceType().databaseName(),
                item.tier() == null ? null : item.tier().name(),
                item.complexity(),
                weight,
                item.pullRequestNumbers(),
                item.sourceUrl());
    }
}
