package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * One row of a judgment. A judgment is never updated and never deleted: a database trigger refuses
 * both, Hibernate issues no update for it and there are no setters. A new judgment of the same
 * subject is a new row that supersedes the old one. {@link JudgmentStore} is the only way to write
 * one.
 */
@Entity
@Immutable
@Table(name = "judgment")
public class Judgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = JudgmentKind.DatabaseConverter.class)
    private JudgmentKind kind;

    private Long rawRecordId;

    private Long evidenceItemId;

    // The JSON text, as in EvidenceItem, so that the canonical form reaches the jsonb column as it is.
    @JdbcTypeCode(SqlTypes.JSON)
    private String value;

    private String tier;

    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    private String facts;

    private BigDecimal confidence;

    @Convert(converter = JudgedBy.DatabaseConverter.class)
    private JudgedBy judgedBy;

    private String model;

    private String promptVersion;

    private String inputHash;

    private Long basedOnJudgmentId;

    private Long supersedesId;

    private Instant judgedAt;

    protected Judgment() {}

    Judgment(NewJudgment judgment, Long supersedesId, Instant judgedAt) {
        this.kind = judgment.kind();
        if (judgment.subject().type() == JudgmentSubject.Type.PULL_REQUEST_RECORD) {
            this.rawRecordId = judgment.subject().id();
        } else {
            this.evidenceItemId = judgment.subject().id();
        }
        this.value = CanonicalJson.canonicalize(judgment.value());
        this.tier = judgment.tier();
        this.reason = judgment.reason();
        this.facts = CanonicalJson.canonicalize(factsAsJson(judgment.facts()));
        this.confidence = judgment.confidence() == null ? null : BigDecimal.valueOf(judgment.confidence());
        this.judgedBy = judgment.judgedBy();
        this.model = judgment.model();
        this.promptVersion = judgment.promptVersion();
        this.inputHash = judgment.inputHash();
        this.basedOnJudgmentId = judgment.basedOnJudgmentId();
        this.supersedesId = supersedesId;
        this.judgedAt = judgedAt;
    }

    private static ArrayNode factsAsJson(List<JudgmentFact> facts) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        facts.forEach(fact -> array.addObject().put("fact", fact.fact()).put("link", fact.link()));
        return array;
    }

    public Long getId() {
        return id;
    }

    public JudgmentKind getKind() {
        return kind;
    }

    /** The pull request record version judged; null when the subject is an evidence item. */
    public Long getRawRecordId() {
        return rawRecordId;
    }

    /** The evidence item row judged; null when the subject is a pull request record. */
    public Long getEvidenceItemId() {
        return evidenceItemId;
    }

    public JsonNode getValue() {
        return CanonicalJson.parse(value);
    }

    /** The tier, when the kind gives one; else null. */
    public String getTier() {
        return tier;
    }

    public String getReason() {
        return reason;
    }

    /** The facts as stored, {@code [{fact, link}]}. */
    public JsonNode getFactsAsJson() {
        return CanonicalJson.parse(facts);
    }

    public List<JudgmentFact> getFacts() {
        return CanonicalJson.parse(facts)
                .valueStream()
                .map(fact -> new JudgmentFact(
                        fact.path("fact").asString(), fact.path("link").asString()))
                .toList();
    }

    /** The model's own value from 0 to 1, when the kind has one; else null. */
    public Double getConfidence() {
        return confidence == null ? null : confidence.doubleValue();
    }

    public JudgedBy getJudgedBy() {
        return judgedBy;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getInputHash() {
        return inputHash;
    }

    /** For an item tier, the digest it was read from; else null. */
    public Long getBasedOnJudgmentId() {
        return basedOnJudgmentId;
    }

    /** The id of the judgment this one replaces; null on the first judgment of a subject. */
    public Long getSupersedesId() {
        return supersedesId;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }
}
