package com.dsinnovators.ei.evidence;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /** The rows with these ids, current or not: an id from an old trail still names its row. */
    @Transactional(readOnly = true)
    public List<EvidenceItem> byIds(Collection<Long> ids) {
        return items.findAllById(ids);
    }

    /** The id of the current row of each of the keys. A key with no current row, or a withdrawn one, is not in the map. */
    @Transactional(readOnly = true)
    public Map<String, Long> currentRowIdsOfKeys(Collection<String> itemKeys) {
        if (itemKeys.isEmpty()) {
            return Map.of();
        }
        return items.findCurrentByKeys(itemKeys).stream()
                .collect(Collectors.toMap(EvidenceItem::getItemKey, EvidenceItem::getId));
    }
}
