package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceItem;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
import com.dsinnovators.ei.judgment.CurrentItemTiers;
import com.dsinnovators.ei.judgment.ItemTier;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows the developers and the current evidence items of each (decision 109). */
@RestController
@RequestMapping("/api/developers")
class DeveloperController {

    private final PersonDirectory persons;
    private final CurrentEvidenceItems evidenceItems;
    private final CurrentItemTiers itemTiers;

    DeveloperController(PersonDirectory persons, CurrentEvidenceItems evidenceItems, CurrentItemTiers itemTiers) {
        this.persons = persons;
        this.evidenceItems = evidenceItems;
        this.itemTiers = itemTiers;
    }

    @GetMapping
    List<DeveloperSummaryResponse> developers() {
        Map<Long, Map<EvidenceType, Long>> counts = evidenceItems.countsPerPerson();
        return persons.all().stream()
                .map(person -> new DeveloperSummaryResponse(
                        person.personId(),
                        person.displayName(),
                        TypeCounts.of(counts.getOrDefault(person.personId(), Map.of()))))
                .toList();
    }

    @GetMapping("/{personId}/evidence-items")
    DeveloperEvidenceResponse evidenceItems(@PathVariable long personId) {
        PersonSummary person = persons.find(personId).orElseThrow(() -> new DeveloperNotFoundException(personId));
        List<EvidenceItem> current = evidenceItems.ofPerson(personId);
        Map<Long, ItemTier> tiers =
                itemTiers.ofItems(current.stream().map(EvidenceItem::getId).toList());
        List<EvidenceItemResponse> items = current.stream()
                .map(item -> EvidenceItemResponse.of(item, tiers.get(item.getId())))
                .toList();
        return new DeveloperEvidenceResponse(new PersonResponse(person.personId(), person.displayName()), items);
    }
}
