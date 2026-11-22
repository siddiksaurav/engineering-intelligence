package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.judgment.ItemTier;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * One evidence item as the web shows it. The title and the links are read from the type-specific
 * {@code fields}, whose shapes the builders of the {@code evidence} package write.
 */
record EvidenceItemResponse(
        long id,
        String type,
        LocalDate date,
        String quarter,
        String title,
        String sourceUrl,
        List<ItemLink> links,
        ItemTier tier,
        JsonNode fields) {

    /** @param tier the current tier of the item; null when it has none */
    static EvidenceItemResponse of(EvidenceItem item, ItemTier tier) {
        JsonNode fields = item.getFields();
        return new EvidenceItemResponse(
                item.getId(),
                item.getEvidenceType().databaseName(),
                item.getOccurredOn(),
                Quarter.of(item.getOccurredOn()).toString(),
                ItemTitleAndLinks.titleOf(item),
                item.getSourceUrl(),
                ItemTitleAndLinks.linksOf(item),
                tier,
                fields);
    }
}
