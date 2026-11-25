package com.dsinnovators.ei.review;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One item of the blind check sample, with the AI judgment the check measures. Never updated and never
 * deleted: a database trigger refuses both, Hibernate issues no update for it and there are no setters.
 * {@link BlindCheckSample} is the only way to write one.
 */
@Entity
@Immutable
@Table(name = "blind_check_item")
public class BlindCheckItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long evidenceItemId;

    private Long judgmentId;

    private int position;

    private Instant pickedAt;

    protected BlindCheckItem() {}

    BlindCheckItem(long evidenceItemId, long judgmentId, int position, Instant pickedAt) {
        this.evidenceItemId = evidenceItemId;
        this.judgmentId = judgmentId;
        this.position = position;
        this.pickedAt = pickedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getEvidenceItemId() {
        return evidenceItemId;
    }

    /** The judgment fixed when the sample was picked; a later judgment of the item does not replace it. */
    public Long getJudgmentId() {
        return judgmentId;
    }

    /** The row of the item in the exported file, from 1; it says nothing about the AI's answer. */
    public int getPosition() {
        return position;
    }

    public Instant getPickedAt() {
        return pickedAt;
    }
}
