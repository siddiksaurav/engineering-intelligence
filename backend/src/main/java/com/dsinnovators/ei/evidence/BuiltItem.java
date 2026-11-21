package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.CanonicalJson;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * An evidence item as a builder made it, before it is stored. The sources are the exact raw record
 * versions the item was built from, so a new version of a source changes the content hash.
 *
 * @param projectId the OpenProject project; null for a Review comment written
 * @param fields the type-specific fields of design "Tables (migration V8)"
 */
public record BuiltItem(
        String key,
        EvidenceType type,
        long personId,
        Long projectId,
        LocalDate occurredOn,
        String sourceUrl,
        JsonNode fields,
        List<EvidenceItemSource> sources) {

    private static final Comparator<EvidenceItemSource> BY_RECORD_THEN_ROLE =
            Comparator.comparing(EvidenceItemSource::rawRecordId).thenComparing(EvidenceItemSource::role);

    public BuiltItem {
        sources = List.copyOf(sources);
    }

    /** SHA-256 of the canonical JSON of everything in the item, the sources included. */
    String contentHash() {
        JsonNodeFactory json = JsonNodeFactory.instance;
        ObjectNode content = json.objectNode();
        content.put("itemKey", key);
        content.put("evidenceType", type.databaseName());
        content.put("personId", personId);
        if (projectId == null) {
            content.putNull("projectId");
        } else {
            content.put("projectId", projectId);
        }
        content.put("occurredOn", occurredOn.toString());
        content.put("sourceUrl", sourceUrl);
        content.set("fields", fields);
        ArrayNode sourceNodes = content.putArray("sources");
        sources.stream().sorted(BY_RECORD_THEN_ROLE).forEach(source -> {
            ObjectNode sourceNode = sourceNodes.addObject();
            sourceNode.put("rawRecordId", source.rawRecordId());
            sourceNode.put("role", source.role().name());
        });
        return CanonicalJson.sha256Hex(content);
    }
}
