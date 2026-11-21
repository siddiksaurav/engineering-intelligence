package com.dsinnovators.ei.api;

import com.dsinnovators.ei.evidence.CurrentEvidenceItems;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.identity.PersonSummary;
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

    DeveloperController(PersonDirectory persons, CurrentEvidenceItems evidenceItems) {
        this.persons = persons;
        this.evidenceItems = evidenceItems;
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
        List<EvidenceItemResponse> items = evidenceItems.ofPerson(personId).stream()
                .map(EvidenceItemResponse::of)
                .toList();
        return new DeveloperEvidenceResponse(new PersonResponse(person.personId(), person.displayName()), items);
    }
}
