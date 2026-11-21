package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.source.DataSource;
import com.dsinnovators.ei.source.DataSourceRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the OpenProject proposal of one person (F04 design, step 6). The person's {@code proposed}
 * row and OpenProject identity are replaced together, in one transaction, so exactly one
 * OpenProject identity stands for the current proposal (decision 95). A decision of the user, a
 * {@code confirmed} or {@code rejected} row, is never touched.
 */
@Component
public class ProposalWriter {

    /** What {@link #write} did. */
    public enum Outcome {
        WRITTEN,
        UNCHANGED,
        REMOVED
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final IdentityProposalRepository proposals;
    private final PersonRepository people;
    private final PersonIdentityRepository identities;
    private final DataSourceRepository dataSources;

    ProposalWriter(
            IdentityProposalRepository proposals,
            PersonRepository people,
            PersonIdentityRepository identities,
            DataSourceRepository dataSources) {
        this.proposals = proposals;
        this.people = people;
        this.identities = identities;
        this.dataSources = dataSources;
    }

    /**
     * Makes the person's proposed row say what the winner says, or removes it when there is no
     * winner. A person who has confirmed an OpenProject user keeps it and nothing is written.
     *
     * @param winner the candidate assigned to the person, if any
     */
    @Transactional
    public Outcome write(long personId, Optional<Candidate> winner) {
        if (proposals.findOf(personId, IdentityProposal.CONFIRMED).isPresent()) {
            return Outcome.UNCHANGED;
        }
        Optional<IdentityProposal> current = proposals.findOf(personId, IdentityProposal.PROPOSED);
        if (winner.isEmpty()) {
            return current.isPresent() ? remove(personId) : Outcome.UNCHANGED;
        }
        String evidence = JSON.writeValueAsString(winner.get().evidence());
        if (current.isPresent() && isSame(current.get(), winner.get(), evidence)) {
            return Outcome.UNCHANGED;
        }
        return replace(personId, winner.get(), evidence);
    }

    private Outcome remove(long personId) {
        proposals.deleteOf(personId, IdentityProposal.PROPOSED);
        identities.deleteOfPerson(IdentitySource.OPENPROJECT, personId);
        return Outcome.REMOVED;
    }

    private Outcome replace(long personId, Candidate winner, String evidence) {
        proposals.deleteOf(personId, IdentityProposal.PROPOSED);
        proposals.save(new IdentityProposal(personId, winner, evidence));
        String externalId = Long.toString(winner.userId());
        identities.deleteOfPerson(IdentitySource.OPENPROJECT, personId);
        // A user whose proposal moved to this person leaves the identity of the person before.
        identities.deleteOfExternalId(IdentitySource.OPENPROJECT, externalId);
        Person person = people.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Person " + personId + " does not exist"));
        identities.save(new PersonIdentity(person, openProject(), externalId, winner.name()));
        return Outcome.WRITTEN;
    }

    private static boolean isSame(IdentityProposal stored, Candidate winner, String evidence) {
        return stored.hasSameFiguresAs(winner) && sameJson(stored.getEvidence(), evidence);
    }

    private static boolean sameJson(String first, String second) {
        JsonNode firstTree = JSON.readTree(first);
        return firstTree.equals(JSON.readTree(second));
    }

    private DataSource openProject() {
        return dataSources
                .findByKey(IdentitySource.OPENPROJECT)
                .orElseThrow(() -> new IllegalStateException(
                        "The data source " + IdentitySource.OPENPROJECT + " is not in the database"));
    }
}
