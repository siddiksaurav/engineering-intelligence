package com.dsinnovators.ei.review;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * The user's blind label of one sample item. Never updated and never deleted: a database trigger
 * refuses both, and there are no setters. A label is given once (decision 333), so no row supersedes
 * another and every row is current; the column {@code supersedes_id} is not mapped. {@link LabelImport} is the only way to write one.
 */
@Entity
@Immutable
@Table(name = "human_label")
public class HumanLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long blindCheckItemId;

    private String complexity;

    private boolean cannotJudge;

    private String note;

    private Instant labeledAt;

    protected HumanLabel() {}

    HumanLabel(long blindCheckItemId, String complexity, boolean cannotJudge, String note, Instant labeledAt) {
        this.blindCheckItemId = blindCheckItemId;
        this.complexity = complexity;
        this.cannotJudge = cannotJudge;
        this.note = note;
        this.labeledAt = labeledAt;
    }

    public Long getBlindCheckItemId() {
        return blindCheckItemId;
    }

    /** One of low, medium, high, very_high; null when the user could not judge the item. */
    public String getComplexity() {
        return complexity;
    }

    public boolean isCannotJudge() {
        return cannotJudge;
    }

    public String getNote() {
        return note;
    }

    public Instant getLabeledAt() {
        return labeledAt;
    }
}
