package com.dsinnovators.ei.review;

import com.dsinnovators.ei.common.CanonicalJson;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * The user's verdict on the AI's reason for one sample item. Never updated and never deleted: a
 * database trigger refuses both, and there are no setters. A changed mark is a new row that supersedes
 * the old one; {@link ReasonMarks} is the only way to write one.
 */
@Entity
@Immutable
@Table(name = "reason_mark")
class ReasonMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long blindCheckItemId;

    private boolean reasonRight;

    // The JSON text, as in Judgment, so that the canonical form reaches the jsonb column as it is.
    @JdbcTypeCode(SqlTypes.JSON)
    private String problemMarks;

    private String note;

    private Instant markedAt;

    private Long supersedesId;

    protected ReasonMark() {}

    ReasonMark(
            long blindCheckItemId,
            boolean reasonRight,
            JsonNode problemMarks,
            String note,
            Instant markedAt,
            Long supersedesId) {
        this.blindCheckItemId = blindCheckItemId;
        this.reasonRight = reasonRight;
        this.problemMarks = CanonicalJson.canonicalize(problemMarks);
        this.note = note;
        this.markedAt = markedAt;
        this.supersedesId = supersedesId;
    }

    Long getId() {
        return id;
    }

    Long getBlindCheckItemId() {
        return blindCheckItemId;
    }

    boolean isReasonRight() {
        return reasonRight;
    }

    /** {@code [{name, verdict}]}, one entry per problem of the judgment, in its order. */
    JsonNode getProblemMarks() {
        return CanonicalJson.parse(problemMarks);
    }

    String getNote() {
        return note;
    }

    Instant getMarkedAt() {
        return markedAt;
    }

    /** The mark this one replaces; null for the first mark of an item. */
    Long getSupersedesId() {
        return supersedesId;
    }
}
