package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.CanonicalJson;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * One row of an evidence item. An item is never updated and never deleted: a database trigger
 * refuses both, Hibernate issues no update for it and there are no setters. A change is a new row
 * that supersedes the old one; a row with {@code withdrawn} set ends the item. {@link
 * EvidenceItemStore} is the only way to write one.
 */
@Entity
@Immutable
@Table(name = "evidence_item")
public class EvidenceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemKey;

    @Convert(converter = EvidenceType.DatabaseConverter.class)
    private EvidenceType evidenceType;

    private Long personId;

    private Long projectId;

    private LocalDate occurredOn;

    private String sourceUrl;

    // The JSON text, as in RawRecord, so that the canonical form reaches the jsonb column as it is.
    @JdbcTypeCode(SqlTypes.JSON)
    private String fields;

    private String contentHash;

    private boolean withdrawn;

    private Long supersedesId;

    private Instant builtAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evidence_item_source", joinColumns = @JoinColumn(name = "evidence_item_id"))
    private Set<EvidenceItemSource> sources;

    protected EvidenceItem() {}

    EvidenceItem(
            String itemKey,
            EvidenceType evidenceType,
            Long personId,
            Long projectId,
            LocalDate occurredOn,
            String sourceUrl,
            JsonNode fields,
            String contentHash,
            boolean withdrawn,
            Long supersedesId,
            Instant builtAt,
            Set<EvidenceItemSource> sources) {
        this.itemKey = itemKey;
        this.evidenceType = evidenceType;
        this.personId = personId;
        this.projectId = projectId;
        this.occurredOn = occurredOn;
        this.sourceUrl = sourceUrl;
        this.fields = CanonicalJson.canonicalize(fields);
        this.contentHash = contentHash;
        this.withdrawn = withdrawn;
        this.supersedesId = supersedesId;
        this.builtAt = builtAt;
        this.sources = Set.copyOf(sources);
    }

    public Long getId() {
        return id;
    }

    /** The stable name of the item across rebuilds. */
    public String getItemKey() {
        return itemKey;
    }

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public Long getPersonId() {
        return personId;
    }

    /** The OpenProject project of the item; null for a Review comment written. */
    public Long getProjectId() {
        return projectId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public JsonNode getFields() {
        return CanonicalJson.parse(fields);
    }

    public String getContentHash() {
        return contentHash;
    }

    /** True on a row that ends an item that no longer follows from the records. */
    public boolean isWithdrawn() {
        return withdrawn;
    }

    /** The id of the row this one replaces; null on the first row of an item. */
    public Long getSupersedesId() {
        return supersedesId;
    }

    public Instant getBuiltAt() {
        return builtAt;
    }

    public Set<EvidenceItemSource> getSources() {
        return sources;
    }
}
