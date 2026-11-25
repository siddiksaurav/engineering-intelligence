package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceItem;
import java.time.LocalDate;
import java.util.List;

/**
 * One evidence item row as the profile page and the item page show it.
 *
 * @param tier the tier of the current judgment of the item's kind; null when it has none
 * @param current false when another row supersedes this one or it is withdrawn
 * @param currentItemId the current row of the same key; null when there is none or this row is it
 */
record EvidenceItemSummaryResponse(
        long id,
        String type,
        LocalDate date,
        String quarter,
        String title,
        String sourceUrl,
        List<ItemLink> links,
        PersonResponse person,
        String tier,
        boolean current,
        Long currentItemId) {

    static EvidenceItemSummaryResponse of(
            EvidenceItem item, PersonResponse person, String tier, Long currentRowIdOfKey) {
        boolean current = item.getId().equals(currentRowIdOfKey);
        return new EvidenceItemSummaryResponse(
                item.getId(),
                item.getEvidenceType().databaseName(),
                item.getOccurredOn(),
                Quarter.of(item.getOccurredOn()).toString(),
                ItemTitleAndLinks.titleOf(item),
                item.getSourceUrl(),
                ItemTitleAndLinks.linksOf(item),
                person,
                tier,
                current,
                current ? null : currentRowIdOfKey);
    }

    EvidenceItemSummaryResponse withTier(String newTier) {
        return new EvidenceItemSummaryResponse(
                id, type, date, quarter, title, sourceUrl, links, person, newTier, current, currentItemId);
    }

    EvidenceItemSummaryResponse withoutTier() {
        return withTier(null);
    }
}
