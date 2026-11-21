package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.CanonicalJson;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only way to write an evidence item. A row is written only when the item is new or its content
 * changed; the old row stays and the new row supersedes it (decisions 106, 123). One transaction
 * per call, so a failed item leaves the others as they are.
 */
@Service
public class EvidenceItemStore {

    private final EvidenceItemRepository items;
    private final Clock clock;

    EvidenceItemStore(EvidenceItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
    }

    /** Writes the item when it is new or differs from the current item of its key. */
    @Transactional
    public WriteOutcome write(BuiltItem built) {
        String contentHash = built.contentHash();
        Optional<EvidenceItem> latest = items.findLatestRowOfKey(built.key());
        if (latest.isPresent()
                && !latest.get().isWithdrawn()
                && latest.get().getContentHash().equals(contentHash)) {
            return WriteOutcome.UNCHANGED;
        }
        items.save(new EvidenceItem(
                built.key(),
                built.type(),
                built.personId(),
                built.projectId(),
                built.occurredOn(),
                built.sourceUrl(),
                built.fields(),
                contentHash,
                false,
                latest.map(EvidenceItem::getId).orElse(null),
                clock.instant(),
                Set.copyOf(built.sources())));
        return latest.isPresent() ? WriteOutcome.SUPERSEDED : WriteOutcome.CREATED;
    }

    /** Ends the current item of the key with a withdrawn row; the item no longer follows from the records. */
    @Transactional
    public void withdraw(String key) {
        EvidenceItem current = items.findCurrentByKey(key)
                .orElseThrow(() -> new IllegalStateException("No current evidence item to withdraw for key " + key));
        ObjectNode content = JsonNodeFactory.instance.objectNode();
        content.put("itemKey", key);
        content.put("withdrawn", true);
        content.put("supersedes", current.getContentHash());
        items.save(new EvidenceItem(
                key,
                current.getEvidenceType(),
                current.getPersonId(),
                current.getProjectId(),
                current.getOccurredOn(),
                current.getSourceUrl(),
                current.getFields(),
                CanonicalJson.sha256Hex(content),
                true,
                current.getId(),
                clock.instant(),
                Set.of()));
    }

    /** The keys of all current, not withdrawn items. */
    @Transactional(readOnly = true)
    public Set<String> currentKeys() {
        return items.findAllCurrent().stream().map(EvidenceItem::getItemKey).collect(Collectors.toUnmodifiableSet());
    }
}
