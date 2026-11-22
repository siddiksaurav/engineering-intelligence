package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * The numbers one gate is checked with (poc-plan 3.3): pull requests of the gate's kind, of which some change tests.
 * Every number is kept as the list it counts, so the trail can open it to its pull requests or developers.
 *
 * @param personTestedPullRequests numbers of the person's pull requests of the kind that change tests
 * @param personPullRequests numbers of all the person's pull requests of the kind, the tested ones included
 * @param repositoryTestedPullRequests numbers of the repository's pull requests of the kind that change tests, all
 *     developers together
 * @param repositoryPullRequests numbers of all the repository's pull requests of the kind
 * @param developersInGroup person ids of the developers with enough pull requests of the kind to count in the group
 * @param pullRequestsWithoutDigest numbers of the person's pull requests left out because they have no digest
 *     (decision 208)
 */
public record GateNumbers(
        List<Integer> personTestedPullRequests,
        List<Integer> personPullRequests,
        List<Integer> repositoryTestedPullRequests,
        List<Integer> repositoryPullRequests,
        List<Long> developersInGroup,
        List<Integer> pullRequestsWithoutDigest) {

    public GateNumbers {
        personTestedPullRequests = List.copyOf(personTestedPullRequests);
        personPullRequests = List.copyOf(personPullRequests);
        repositoryTestedPullRequests = List.copyOf(repositoryTestedPullRequests);
        repositoryPullRequests = List.copyOf(repositoryPullRequests);
        developersInGroup = List.copyOf(developersInGroup);
        pullRequestsWithoutDigest = List.copyOf(pullRequestsWithoutDigest);
        if (personTestedPullRequests.size() > personPullRequests.size()
                || repositoryTestedPullRequests.size() > repositoryPullRequests.size()) {
            throw new IllegalArgumentException("More pull requests change tests than there are pull requests: person "
                    + personTestedPullRequests.size() + " of " + personPullRequests.size() + ", repository "
                    + repositoryTestedPullRequests.size() + " of " + repositoryPullRequests.size());
        }
    }

    long personTested() {
        return personTestedPullRequests.size();
    }

    long personTotal() {
        return personPullRequests.size();
    }

    long repositoryTested() {
        return repositoryTestedPullRequests.size();
    }

    long repositoryTotal() {
        return repositoryPullRequests.size();
    }
}
