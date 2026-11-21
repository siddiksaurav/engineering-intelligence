package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class IdentityProposalFunctionsTest extends DatabaseTest {

    private static final long OPENPROJECT_USER_ID = 80250L;

    private long personId() {
        jdbc.update("insert into person (display_name) values ('akabiru')");
        return jdbc.queryForObject("select id from person where display_name = 'akabiru'", Long.class);
    }

    private long insertProposal(long personId, long userId, String state) {
        return jdbc.queryForObject("""
                insert into identity_proposal
                    (person_id, openproject_user_id, openproject_user_name, supporting_items, status_change_items,
                     person_items, evidence, state)
                values (?, ?, 'Kabiru Mwenja', 1, 0, 1, '[]'::jsonb, ?)
                returning id
                """, Long.class, personId, userId, state);
    }

    private void insertOpenProjectIdentity(long personId, long userId) {
        jdbc.update("""
                insert into person_identity (person_id, data_source_id, external_id, username)
                values (?, (select id from data_source where key = 'openproject'), ?, 'Kabiru Mwenja')
                """, personId, Long.toString(userId));
    }

    private Map<String, Object> proposal(long id) {
        return jdbc.queryForMap("select state, decided_at, proposed_at from identity_proposal where id = ?", id);
    }

    private int openProjectIdentities(long personId) {
        return jdbc.queryForObject("""
                select count(*) from person_identity
                where person_id = ? and data_source_id = (select id from data_source where key = 'openproject')
                """, Integer.class, personId);
    }

    @Test
    void rejectMarksTheProposalRejectedAndRemovesTheOpenProjectIdentity() {
        long person = personId();
        long proposal = insertProposal(person, OPENPROJECT_USER_ID, "proposed");
        insertOpenProjectIdentity(person, OPENPROJECT_USER_ID);

        jdbc.queryForObject("select reject_identity_proposal(?)", Object.class, proposal);

        Map<String, Object> row = proposal(proposal);
        assertEquals("rejected", row.get("state"));
        assertNotNull(row.get("decided_at"));
        assertEquals(0, openProjectIdentities(person));
    }

    @Test
    void rejectOfAConfirmedProposalAlsoWorks() {
        long person = personId();
        long proposal = insertProposal(person, OPENPROJECT_USER_ID, "confirmed");
        insertOpenProjectIdentity(person, OPENPROJECT_USER_ID);

        jdbc.queryForObject("select reject_identity_proposal(?)", Object.class, proposal);

        assertEquals("rejected", proposal(proposal).get("state"));
        assertEquals(0, openProjectIdentities(person));
    }

    @Test
    void rejectKeepsTheOpenProjectIdentityOfAnotherUser() {
        long person = personId();
        long proposal = insertProposal(person, OPENPROJECT_USER_ID, "proposed");
        insertOpenProjectIdentity(person, 99L);

        jdbc.queryForObject("select reject_identity_proposal(?)", Object.class, proposal);

        assertEquals(1, openProjectIdentities(person));
    }

    @Test
    void rejectOfARejectedProposalNamesTheId() {
        long proposal = insertProposal(personId(), OPENPROJECT_USER_ID, "rejected");

        DataAccessException failure = assertThrows(
                DataAccessException.class,
                () -> jdbc.queryForObject("select reject_identity_proposal(?)", Object.class, proposal));

        assertTrue(failure.getMessage().contains("Identity proposal " + proposal + " is rejected"));
    }

    @Test
    void rejectOfAnUnknownIdNamesTheId() {
        DataAccessException failure = assertThrows(
                DataAccessException.class,
                () -> jdbc.queryForObject("select reject_identity_proposal(?)", Object.class, 4242L));

        assertTrue(failure.getMessage().contains("Identity proposal 4242 does not exist"));
    }

    @Test
    void confirmMarksAProposedProposalConfirmed() {
        long proposal = insertProposal(personId(), OPENPROJECT_USER_ID, "proposed");
        assertNull(proposal(proposal).get("decided_at"));

        jdbc.queryForObject("select confirm_identity_proposal(?)", Object.class, proposal);

        Map<String, Object> row = proposal(proposal);
        assertEquals("confirmed", row.get("state"));
        assertNotNull(row.get("decided_at"));
    }

    @Test
    void confirmOfARejectedProposalNamesTheIdAndTheState() {
        long proposal = insertProposal(personId(), OPENPROJECT_USER_ID, "rejected");

        DataAccessException failure = assertThrows(
                DataAccessException.class,
                () -> jdbc.queryForObject("select confirm_identity_proposal(?)", Object.class, proposal));

        assertTrue(failure.getMessage().contains("Identity proposal " + proposal + " is rejected"));
        assertEquals("rejected", proposal(proposal).get("state"));
    }

    @Test
    void confirmOfAnUnknownIdNamesTheId() {
        DataAccessException failure = assertThrows(
                DataAccessException.class,
                () -> jdbc.queryForObject("select confirm_identity_proposal(?)", Object.class, 4242L));

        assertTrue(failure.getMessage().contains("Identity proposal 4242 does not exist"));
    }

    @Test
    void aPersonHasAtMostOneProposedOrConfirmedProposal() {
        long person = personId();
        insertProposal(person, 1L, "proposed");

        assertThrows(DataIntegrityViolationException.class, () -> insertProposal(person, 2L, "confirmed"));
    }

    @Test
    void aPersonMayHaveManyRejectedProposalsAndOneOpenOne() {
        long person = personId();
        insertProposal(person, 1L, "rejected");
        insertProposal(person, 2L, "rejected");

        insertProposal(person, 3L, "proposed");
    }

    @Test
    void aPairOfPersonAndUserIsUnique() {
        long person = personId();
        insertProposal(person, 1L, "rejected");

        assertThrows(DataIntegrityViolationException.class, () -> insertProposal(person, 1L, "rejected"));
    }

    @Test
    void aStateOutsideTheThreeStatesIsRefused() {
        long person = personId();

        assertThrows(DataIntegrityViolationException.class, () -> insertProposal(person, 1L, "maybe"));
    }
}
