package com.dsinnovators.ei.evidence;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the current items: the rows no other row supersedes, leaving out the withdrawn ones. */
@Service
public class CurrentEvidenceItems {

    private final EvidenceItemRepository items;

    CurrentEvidenceItems(EvidenceItemRepository items) {
        this.items = items;
    }

    /** The current items of a person, newest date first. */
    @Transactional(readOnly = true)
    public List<EvidenceItem> ofPerson(long personId) {
        return items.findCurrentOfPerson(personId);
    }

    /** For each person with a current item, the number of current items per type. */
    @Transactional(readOnly = true)
    public Map<Long, Map<EvidenceType, Long>> countsPerPerson() {
        Map<Long, Map<EvidenceType, Long>> counts = new HashMap<>();
        for (EvidenceItem item : items.findAllCurrent()) {
            counts.computeIfAbsent(item.getPersonId(), person -> new EnumMap<>(EvidenceType.class))
                    .merge(item.getEvidenceType(), 1L, Long::sum);
        }
        return counts;
    }
}
