package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
import com.dsinnovators.ei.judgment.CurrentItemTiers;
import com.dsinnovators.ei.judgment.ItemTier;
import com.dsinnovators.ei.scoring.ScoredItem;
import com.dsinnovators.ei.scoring.ScoredItems;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shows the developers of a quarter and the current evidence items of each in it (decisions 109, 283, 296). */
@RestController
@RequestMapping("/api/developers")
class DeveloperController {

    private final PersonDirectory persons;
    private final CurrentEvidenceItems evidenceItems;
    private final CurrentItemTiers itemTiers;
    private final ScoredItems scoredItems;

    DeveloperController(
            PersonDirectory persons,
            CurrentEvidenceItems evidenceItems,
            CurrentItemTiers itemTiers,
            ScoredItems scoredItems) {
        this.persons = persons;
        this.evidenceItems = evidenceItems;
        this.itemTiers = itemTiers;
        this.scoredItems = scoredItems;
    }

    /** The developers scored in the quarter, with the number of their items of each type in it. */
    @GetMapping
    List<DeveloperSummaryResponse> developers(@RequestParam(name = "quarter", required = false) String quarterText) {
        Quarter quarter = QuarterParameter.parse(quarterText);
        return scoredItems.of(quarter).entrySet().stream()
                .map(entry -> summaryOf(entry.getKey(), entry.getValue()))
                .toList();
    }

    private DeveloperSummaryResponse summaryOf(long personId, List<ScoredItem> items) {
        PersonSummary person = persons.find(personId).orElseThrow(() -> new DeveloperNotFoundException(personId));
        Map<EvidenceType, Long> counts =
                items.stream().collect(Collectors.groupingBy(ScoredItem::evidenceType, Collectors.counting()));
        return new DeveloperSummaryResponse(person.personId(), person.displayName(), TypeCounts.of(counts));
    }

    @GetMapping("/{personId}/evidence-items")
    DeveloperEvidenceResponse evidenceItems(
            @PathVariable long personId, @RequestParam(name = "quarter", required = false) String quarterText) {
        Quarter quarter = QuarterParameter.parse(quarterText);
        PersonSummary person = persons.find(personId).orElseThrow(() -> new DeveloperNotFoundException(personId));
        List<EvidenceItem> current = evidenceItems.ofPerson(personId, quarter);
        Map<Long, ItemTier> tiers =
                itemTiers.ofItems(current.stream().map(EvidenceItem::getId).toList());
        List<EvidenceItemResponse> items = current.stream()
                .map(item -> EvidenceItemResponse.of(item, tiers.get(item.getId())))
                .toList();
        return new DeveloperEvidenceResponse(new PersonResponse(person.personId(), person.displayName()), items);
    }
}
