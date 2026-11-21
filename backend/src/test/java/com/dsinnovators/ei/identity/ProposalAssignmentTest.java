package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.identity.ProposalAssignment.PersonUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ProposalAssignmentTest {

    private static final long ANNA = 1L;
    private static final long BORIS = 2L;
    private static final long USER_A = 100L;
    private static final long USER_B = 200L;

    private static Candidate candidate(long userId, int supportingItems, int statusChangeItems) {
        return new Candidate(userId, "User " + userId, supportingItems, statusChangeItems, supportingItems, List.of());
    }

    private static Map<Long, Long> assignedUsers(Map<Long, List<Candidate>> candidates) {
        return assignedUsers(candidates, Set.of(), Set.of());
    }

    private static Map<Long, Long> assignedUsers(
            Map<Long, List<Candidate>> candidates, Set<PersonUser> rejected, Set<PersonUser> confirmed) {
        Map<Long, Long> users = new TreeMap<>();
        ProposalAssignment.assign(candidates, rejected, confirmed)
                .forEach((personId, candidate) -> users.put(personId, candidate.userId()));
        return users;
    }

    @Test
    void theStrongerPersonGetsASharedUserAndTheOtherGetsTheirNextCandidate() {
        Map<Long, List<Candidate>> candidates = Map.of(
                ANNA, List.of(candidate(USER_A, 5, 0), candidate(USER_B, 1, 0)),
                BORIS, List.of(candidate(USER_A, 2, 0), candidate(USER_B, 1, 0)));

        assertEquals(Map.of(ANNA, USER_A, BORIS, USER_B), assignedUsers(candidates));
    }

    @Test
    void aUserTwoEquallyStrongPersonsWantGoesToNobodyNotEvenAWeakerPerson() {
        long carla = 3L;
        Map<Long, List<Candidate>> candidates = Map.of(
                ANNA, List.of(candidate(USER_A, 3, 0), candidate(USER_B, 1, 0)),
                BORIS, List.of(candidate(USER_A, 3, 0)),
                carla, List.of(candidate(USER_A, 1, 0)));

        assertEquals(Map.of(ANNA, USER_B), assignedUsers(candidates));
    }

    @Test
    void theMostSupportingItemsWinsForOnePerson() {
        assertEquals(
                Map.of(ANNA, USER_B),
                assignedUsers(Map.of(ANNA, List.of(candidate(USER_A, 1, 9), candidate(USER_B, 2, 0)))));
    }

    @Test
    void aTieOnSupportingItemsIsBrokenByStatusChanges() {
        assertEquals(
                Map.of(ANNA, USER_B),
                assignedUsers(Map.of(ANNA, List.of(candidate(USER_A, 2, 1), candidate(USER_B, 2, 3)))));
    }

    @Test
    void aFullTieBetweenTheBestTwoCandidatesGivesNoProposal() {
        assertTrue(assignedUsers(Map.of(ANNA, List.of(candidate(USER_A, 2, 1), candidate(USER_B, 2, 1))))
                .isEmpty());
    }

    @Test
    void aTieBelowTheBestCandidateDoesNotMatter() {
        assertEquals(
                Map.of(ANNA, USER_A),
                assignedUsers(Map.of(
                        ANNA, List.of(candidate(USER_A, 3, 0), candidate(USER_B, 1, 0), candidate(300L, 1, 0)))));
    }

    @Test
    void aRejectedPairIsSkippedAndTheNextCandidateIsProposed() {
        Map<Long, List<Candidate>> candidates = Map.of(ANNA, List.of(candidate(USER_A, 5, 0), candidate(USER_B, 1, 0)));

        assertEquals(Map.of(ANNA, USER_B), assignedUsers(candidates, Set.of(new PersonUser(ANNA, USER_A)), Set.of()));
    }

    @Test
    void aRejectedPairOfAnotherPersonDoesNotBlockThisPerson() {
        Map<Long, List<Candidate>> candidates = Map.of(ANNA, List.of(candidate(USER_A, 5, 0)));

        assertEquals(Map.of(ANNA, USER_A), assignedUsers(candidates, Set.of(new PersonUser(BORIS, USER_A)), Set.of()));
    }

    @Test
    void aConfirmedUserIsNeverGivenToAnotherPersonAndItsPersonGetsNothingNew() {
        Map<Long, List<Candidate>> candidates = Map.of(
                ANNA, List.of(candidate(USER_A, 1, 0)),
                BORIS, List.of(candidate(USER_A, 9, 0), candidate(USER_B, 1, 0)));

        assertEquals(Map.of(BORIS, USER_B), assignedUsers(candidates, Set.of(), Set.of(new PersonUser(ANNA, USER_A))));
    }

    @Test
    void aConfirmedPersonGetsNoOtherCandidate() {
        Map<Long, List<Candidate>> candidates = Map.of(ANNA, List.of(candidate(USER_A, 1, 0), candidate(USER_B, 9, 0)));

        assertTrue(assignedUsers(candidates, Set.of(), Set.of(new PersonUser(ANNA, USER_A)))
                .isEmpty());
    }

    @Test
    void oneSupportingItemIsEnough() {
        assertEquals(Map.of(ANNA, USER_A), assignedUsers(Map.of(ANNA, List.of(candidate(USER_A, 1, 0)))));
    }

    @Test
    void twoPersonsEqualForOneUserBothGoOnToTheirNextCandidate() {
        Map<Long, List<Candidate>> candidates = Map.of(
                ANNA, List.of(candidate(USER_A, 3, 1), candidate(USER_B, 1, 0)),
                BORIS, List.of(candidate(USER_A, 3, 1), candidate(300L, 1, 0)));

        assertEquals(Map.of(ANNA, USER_B, BORIS, 300L), assignedUsers(candidates));
    }

    @Test
    void twoPersonsEqualForTheirOnlyUserBothGetNothing() {
        Map<Long, List<Candidate>> candidates = Map.of(
                ANNA, List.of(candidate(USER_A, 3, 1)),
                BORIS, List.of(candidate(USER_A, 3, 1)));

        assertTrue(assignedUsers(candidates).isEmpty());
    }

    @Test
    void aPersonWithoutCandidatesGetsNothing() {
        assertTrue(assignedUsers(Map.of(ANNA, List.of())).isEmpty());
    }
}
