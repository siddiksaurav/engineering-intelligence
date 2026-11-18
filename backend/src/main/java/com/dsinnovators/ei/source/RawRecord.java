package com.dsinnovators.ei.source;

import com.dsinnovators.ei.common.CanonicalJson;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * An unchanged copy of one pulled record, one row per version. A raw record is never updated and
 * never deleted: a database trigger refuses both, Hibernate issues no update for it, there are no
 * setters, and {@link RawRecordStore} is the only way to write one.
 */
@Entity
@Immutable
@Table(name = "raw_record")
public class RawRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "data_source_id")
    private DataSource dataSource;

    private String recordKind;

    private String externalId;

    private int version;

    private String url;

    private String contentHash;

    // The JSON text. Kept as text in the entity so that every JSON value, the literal null
    // included, goes to the jsonb column as it is.
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant fetchedAt;

    protected RawRecord() {}

    RawRecord(
            DataSource dataSource,
            String recordKind,
            String externalId,
            int version,
            String url,
            String contentHash,
            JsonNode payload,
            Instant fetchedAt) {
        this.dataSource = dataSource;
        this.recordKind = recordKind;
        this.externalId = externalId;
        this.version = version;
        this.url = url;
        this.contentHash = contentHash;
        this.payload = CanonicalJson.canonicalize(payload);
        this.fetchedAt = fetchedAt;
    }

    public Long getId() {
        return id;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getRecordKind() {
        return recordKind;
    }

    public String getExternalId() {
        return externalId;
    }

    public int getVersion() {
        return version;
    }

    public String getUrl() {
        return url;
    }

    public String getContentHash() {
        return contentHash;
    }

    public JsonNode getPayload() {
        return CanonicalJson.parse(payload);
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
