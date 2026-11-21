package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.identity.EvidenceEntry.Signal;
import com.dsinnovators.ei.identity.ProposalWriter.Outcome;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProposalWriterTest extends DatabaseTest {

    private static final long KABIRU = 80250L;
    private static final long BRUNO = 99165L;

    @Autowired
    private ProposalWriter writer;

    private long personId;

    @BeforeEach
    void createPerson() {
        personId = jdbc.queryForObject("insert into person (display_name) values ('akabiru') returning id", Long.class);
    }

    private static Candidate candidate(long userId, String name, int supportingItems) {
        return new Candidate(
                userId,
                name,
                supportingItems,
                1,
                3,
                List.of(
                        new EvidenceEntry(100, "https://community.openproject.org/wp/100", Signal.DEVELOPERS, null),
                        new EvidenceEntry(
                                100,
                                "https://community.openproject.org/wp/100",
                                Signal.STATUS_CHANGE,
                                Instant.parse("2026-07-02T10:00:00Z"))));
    }

    private List<Map<String, Object>> proposals() {
        return jdbc.queryForList("""
                select openproject_user_id, openproject_user_name, supporting_items, state, evidence::text as evidence
                from identity_proposal where person_id = ? order by id
                """, personId);
    }

    private List<String> openProjectUsers() {
        return jdbc.queryForList("""
                select i.external_id || ':' || i.username
                from person_identity i join data_source d on d.id = i.data_source_id
                where d.key = 'openproject' and i.person_id = ? order by i.external_id
                """, String.class, personId);
    }

    @Test
    void aNewWinnerReplacesTheProposedRowAndItsOpenProjectIdentity() {
        assertEquals(Outcome.WRITTEN, writer.write(personId, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 2))));

        assertEquals(Outcome.WRITTEN, writer.write(personId, Optional.of(candidate(BRUNO, "Bruno Pagno", 4))));

        List<Map<String, Object>> proposals = proposals();
        assertEquals(1, proposals.size());
        assertEquals(BRUNO, proposals.get(0).get("openproject_user_id"));
        assertEquals("Bruno Pagno", proposals.get(0).get("openproject_user_name"));
        assertEquals(4, proposals.get(0).get("supporting_items"));
        assertEquals("proposed", proposals.get(0).get("state"));
        assertEquals(List.of(BRUNO + ":Bruno Pagno"), openProjectUsers());
    }

    @Test
    void theEvidenceIsStoredWithOneEntryPerSignal() {
        writer.write(personId, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 2)));

        assertEquals(
                "[{\"at\": null, \"url\": \"https://community.openproject.org/wp/100\", \"signal\": \"DEVELOPERS\","
                        + " \"workPackage\": 100}, {\"at\": \"2026-07-02T10:00:00Z\", \"url\":"
                        + " \"https://community.openproject.org/wp/100\", \"signal\": \"STATUS_CHANGE\","
                        + " \"workPackage\": 100}]",
                proposals().get(0).get("evidence"));
    }

    @Test
    void theSameCandidateTwiceChangesNothing() {
        Candidate kabiru = candidate(KABIRU, "Kabiru Mwenja", 2);
        writer.write(personId, Optional.of(kabiru));
        Object proposedAt = jdbc.queryForObject("select proposed_at from identity_proposal", Object.class);

        assertEquals(Outcome.UNCHANGED, writer.write(personId, Optional.of(kabiru)));

        assertEquals(1, proposals().size());
        assertEquals(proposedAt, jdbc.queryForObject("select proposed_at from identity_proposal", Object.class));
        assertEquals(List.of(KABIRU + ":Kabiru Mwenja"), openProjectUsers());
    }

    @Test
    void noWinnerRemovesTheProposedRowAndItsIdentity() {
        writer.write(personId, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 2)));

        assertEquals(Outcome.REMOVED, writer.write(personId, Optional.empty()));

        assertEquals(List.of(), proposals());
        assertEquals(List.of(), openProjectUsers());
    }

    @Test
    void noWinnerAndNoProposedRowIsUnchanged() {
        assertEquals(Outcome.UNCHANGED, writer.write(personId, Optional.empty()));
    }

    @Test
    void aConfirmedRowAndItsIdentityAreKeptUntouched() {
        writer.write(personId, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 2)));
        jdbc.execute("select confirm_identity_proposal(id) from identity_proposal");

        assertEquals(Outcome.UNCHANGED, writer.write(personId, Optional.of(candidate(BRUNO, "Bruno Pagno", 4))));
        assertEquals(Outcome.UNCHANGED, writer.write(personId, Optional.empty()));

        assertEquals(1, proposals().size());
        assertEquals("confirmed", proposals().get(0).get("state"));
        assertEquals(KABIRU, proposals().get(0).get("openproject_user_id"));
        assertEquals(List.of(KABIRU + ":Kabiru Mwenja"), openProjectUsers());
    }

    @Test
    void aRejectedRowIsKeptAndANewProposalIsWrittenBesideIt() {
        writer.write(personId, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 2)));
        jdbc.execute("select reject_identity_proposal(id) from identity_proposal");

        assertEquals(Outcome.WRITTEN, writer.write(personId, Optional.of(candidate(BRUNO, "Bruno Pagno", 1))));

        assertEquals(
                List.of("rejected", "proposed"),
                proposals().stream().map(row -> row.get("state")).toList());
        assertEquals(List.of(BRUNO + ":Bruno Pagno"), openProjectUsers());
    }

    @Test
    void aUserMovedToAnotherPersonLeavesTheirOldIdentity() {
        long otherPerson =
                jdbc.queryForObject("insert into person (display_name) values ('other') returning id", Long.class);
        writer.write(otherPerson, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 1)));

        writer.write(personId, Optional.of(candidate(KABIRU, "Kabiru Mwenja", 2)));

        assertEquals(List.of(KABIRU + ":Kabiru Mwenja"), openProjectUsers());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from person_identity where person_id = ?", Integer.class, otherPerson));
    }
}
