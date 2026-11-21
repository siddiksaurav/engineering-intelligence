package com.dsinnovators.ei.identity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Chooses at most one OpenProject user for each person, over all persons together (F04 design,
 * step 5; decisions 93, 94, 97). Pure logic: it reads no database.
 *
 * <p>The pairs of person and candidate are taken strongest first, by supporting items and then by
 * status changes. A pair is assigned when its person has nothing yet and its user is not taken. Among
 * pairs of equal strength, a person with two of them is ambiguous and gets nothing, and a user wanted
 * by two persons goes to neither of them from that pair.
 */
final class ProposalAssignment {

    /** A person and an OpenProject user, as a decision of the user ties them. */
    record PersonUser(long personId, long userId) {}

    private record Strength(int supportingItems, int statusChangeItems) {}

    private record Claim(long personId, Candidate candidate) {}

    private static final Comparator<Strength> STRONGEST_FIRST = Comparator.comparingInt(Strength::supportingItems)
            .thenComparingInt(Strength::statusChangeItems)
            .reversed();

    private final Set<Long> settledPersons = new HashSet<>();
    private final Set<Long> takenUsers = new HashSet<>();
    private final SortedMap<Long, Candidate> assigned = new TreeMap<>();

    private ProposalAssignment() {}

    /**
     * The candidate assigned to each person that gets one, by person id. A person with a confirmed
     * pair is left out: they keep it and get nothing else. A confirmed user is never given to
     * another person.
     *
     * @param candidatesByPerson the candidates of each person, by person id
     * @param rejected pairs the user rejected; never assigned again
     * @param confirmed pairs the user confirmed
     */
    static Map<Long, Candidate> assign(
            Map<Long, List<Candidate>> candidatesByPerson, Set<PersonUser> rejected, Set<PersonUser> confirmed) {
        ProposalAssignment assignment = new ProposalAssignment();
        confirmed.forEach(pair -> {
            assignment.settledPersons.add(pair.personId());
            assignment.takenUsers.add(pair.userId());
        });
        claimsByStrength(candidatesByPerson, rejected).values().forEach(assignment::resolve);
        return assignment.assigned;
    }

    private static SortedMap<Strength, List<Claim>> claimsByStrength(
            Map<Long, List<Candidate>> candidatesByPerson, Set<PersonUser> rejected) {
        SortedMap<Strength, List<Claim>> claims = new TreeMap<>(STRONGEST_FIRST);
        candidatesByPerson.forEach((personId, candidates) -> candidates.stream()
                .filter(candidate -> !rejected.contains(new PersonUser(personId, candidate.userId())))
                .forEach(candidate -> claims.computeIfAbsent(
                                new Strength(candidate.supportingItems(), candidate.statusChangeItems()),
                                strength -> new ArrayList<>())
                        .add(new Claim(personId, candidate))));
        return claims;
    }

    private void resolve(List<Claim> claimsOfEqualStrength) {
        List<Claim> open = claimsOfEqualStrength.stream()
                .filter(claim -> !settledPersons.contains(claim.personId())
                        && !takenUsers.contains(claim.candidate().userId()))
                .toList();
        Set<Long> ambiguousPersons = repeated(open, Claim::personId);
        settledPersons.addAll(ambiguousPersons);
        List<Claim> unambiguous = open.stream()
                .filter(claim -> !ambiguousPersons.contains(claim.personId()))
                .toList();
        Set<Long> contestedUsers =
                repeated(unambiguous, claim -> claim.candidate().userId());
        unambiguous.stream()
                .filter(claim -> !contestedUsers.contains(claim.candidate().userId()))
                .forEach(this::assign);
    }

    private void assign(Claim claim) {
        assigned.put(claim.personId(), claim.candidate());
        settledPersons.add(claim.personId());
        takenUsers.add(claim.candidate().userId());
    }

    private static Set<Long> repeated(List<Claim> claims, ToLongFunction<Claim> key) {
        return claims.stream()
                .collect(Collectors.groupingBy(key::applyAsLong, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
