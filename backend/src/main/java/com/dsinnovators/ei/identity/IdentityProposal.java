package com.dsinnovators.ei.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The OpenProject user proposed for a person, with the evidence it rests on. The job rewrites a
 * {@code proposed} row; a {@code confirmed} or {@code rejected} row records the user's decision
 * and is never changed here (the database functions {@code confirm_identity_proposal} and
 * {@code reject_identity_proposal} set it).
 */
@Entity
@Table(name = "identity_proposal")
class IdentityProposal {

    static final String PROPOSED = "proposed";
    static final String CONFIRMED = "confirmed";
    static final String REJECTED = "rejected";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long personId;

    private long openprojectUserId;

    private String openprojectUserName;

    private int supportingItems;

    private int statusChangeItems;

    private int personItems;

    // The JSON text of the evidence entries; kept as text so the column is written as it is.
    @JdbcTypeCode(SqlTypes.JSON)
    private String evidence;

    private String state;

    protected IdentityProposal() {}

    IdentityProposal(long personId, Candidate candidate, String evidence) {
        this.personId = personId;
        this.openprojectUserId = candidate.userId();
        this.openprojectUserName = candidate.name();
        this.supportingItems = candidate.supportingItems();
        this.statusChangeItems = candidate.statusChangeItems();
        this.personItems = candidate.personItems();
        this.evidence = evidence;
        this.state = PROPOSED;
    }

    long getPersonId() {
        return personId;
    }

    String getState() {
        return state;
    }

    String getEvidence() {
        return evidence;
    }

    /** Whether this row says what the candidate says, apart from the evidence. */
    boolean hasSameFiguresAs(Candidate candidate) {
        return openprojectUserId == candidate.userId()
                && openprojectUserName.equals(candidate.name())
                && supportingItems == candidate.supportingItems()
                && statusChangeItems == candidate.statusChangeItems()
                && personItems == candidate.personItems();
    }
}
