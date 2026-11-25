package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
import com.dsinnovators.ei.review.BlindCheckItem;
import com.dsinnovators.ei.review.BlindCheckSample;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The sample as the file the user labels (F08 design, flow 2). It reads the sample and the items,
 * never a judgment, so no answer of the AI can reach a cell.
 */
@Component
class BlindCheckExport {

    private static final List<String> COLUMNS =
            List.of("item_id", "developer", "title", "work_item", "pull_requests", "complexity", "note");

    private final BlindCheckSample sample;
    private final CurrentEvidenceItems evidenceItems;
    private final PersonDirectory persons;

    BlindCheckExport(BlindCheckSample sample, CurrentEvidenceItems evidenceItems, PersonDirectory persons) {
        this.sample = sample;
        this.evidenceItems = evidenceItems;
        this.persons = persons;
    }

    /** The CSV of the stored sample in the order of its positions; the header only when no sample is stored. */
    String csv() {
        List<BlindCheckItem> sampled = sample.stored();
        Map<Long, EvidenceItem> items =
                evidenceItems
                        .byIds(sampled.stream()
                                .map(BlindCheckItem::getEvidenceItemId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(EvidenceItem::getId, Function.identity()));
        Map<Long, PersonSummary> people = persons.find(items.values().stream()
                .map(EvidenceItem::getPersonId)
                .distinct()
                .toList());
        CsvWriter file = new CsvWriter().row(COLUMNS);
        for (BlindCheckItem sampledItem : sampled) {
            EvidenceItem item = items.get(sampledItem.getEvidenceItemId());
            file.row(List.of(
                    String.valueOf(item.getId()),
                    people.get(item.getPersonId()).displayName(),
                    ItemTitleAndLinks.titleOf(item),
                    ItemTitleAndLinks.workItemLinkOf(item).map(ItemLink::url).orElse(""),
                    ItemTitleAndLinks.pullRequestLinksOf(item).stream()
                            .map(ItemLink::url)
                            .collect(Collectors.joining(" ")),
                    "",
                    ""));
        }
        return file.text();
    }
}
