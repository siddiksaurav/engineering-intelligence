package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
import com.dsinnovators.ei.judgment.CurrentItemJudgments;
import com.dsinnovators.ei.judgment.ItemJudgment;
import com.dsinnovators.ei.judgment.JudgmentKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Reads evidence item rows by id and puts each together with its person, its current row and its current judgment. */
@Component
class EvidenceItemSummaries {

    private final CurrentEvidenceItems evidenceItems;
    private final PersonDirectory persons;
    private final CurrentItemJudgments judgments;

    EvidenceItemSummaries(CurrentEvidenceItems evidenceItems, PersonDirectory persons, CurrentItemJudgments judgments) {
        this.evidenceItems = evidenceItems;
        this.persons = persons;
        this.judgments = judgments;
    }

    /** The summary of each id that names a row, in the order asked. An unknown id is left out. */
    List<EvidenceItemSummaryResponse> ofIds(List<Long> ids) {
        Map<Long, EvidenceItem> rows =
                evidenceItems.byIds(ids).stream().collect(Collectors.toMap(EvidenceItem::getId, Function.identity()));
        Collection<EvidenceItem> found = rows.values();
        Map<Long, PersonSummary> people = persons.find(
                found.stream().map(EvidenceItem::getPersonId).distinct().toList());
        Map<String, Long> currentRows = evidenceItems.currentRowIdsOfKeys(
                found.stream().map(EvidenceItem::getItemKey).distinct().toList());
        Map<Long, ItemJudgment> currentJudgments = currentJudgmentsOf(found);
        return ids.stream()
                .filter(rows::containsKey)
                .map(rows::get)
                .map(item -> EvidenceItemSummaryResponse.of(
                        item,
                        personOf(people.get(item.getPersonId())),
                        tierOf(currentJudgments.get(item.getId())),
                        currentRows.get(item.getItemKey())))
                .toList();
    }

    private Map<Long, ItemJudgment> currentJudgmentsOf(Collection<EvidenceItem> items) {
        Map<JudgmentKind, List<Long>> idsPerKind = new HashMap<>();
        for (EvidenceItem item : items) {
            ItemJudgmentKind.of(item.getEvidenceType())
                    .ifPresent(kind -> idsPerKind
                            .computeIfAbsent(kind, ignored -> new ArrayList<>())
                            .add(item.getId()));
        }
        Map<Long, ItemJudgment> current = new HashMap<>();
        idsPerKind.forEach((kind, itemIds) -> current.putAll(judgments.ofItems(kind, itemIds)));
        return current;
    }

    private static PersonResponse personOf(PersonSummary person) {
        return new PersonResponse(person.personId(), person.displayName());
    }

    private static String tierOf(ItemJudgment judgment) {
        return judgment == null ? null : judgment.tier();
    }
}
