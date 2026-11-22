package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * The counts one gate is checked with (poc-plan 3.3): pull requests of the gate's kind, of which some change tests.
 *
 * @param personTestedPullRequests numbers of the person's pull requests of the kind that change tests
 * @param personPullRequests numbers of all the person's pull requests of the kind, the tested ones included
 * @param repositoryTested the repository's pull requests of the kind that change tests, all developers together
 * @param repositoryTotal all the repository's pull requests of the kind
 * @param developersInGroup the developers with enough pull requests of the kind to count in the group
 * @param pullRequestsWithoutDigest the person's pull requests left out because they have no digest (decision 208)
 */
public record GateNumbers(
        List<Integer> personTestedPullRequests,
        List<Integer> personPullRequests,
        long repositoryTested,
        long repositoryTotal,
        int developersInGroup,
        long pullRequestsWithoutDigest) {

    public GateNumbers {
        personTestedPullRequests = List.copyOf(personTestedPullRequests);
        personPullRequests = List.copyOf(personPullRequests);
        if (personTestedPullRequests.size() > personPullRequests.size() || repositoryTested > repositoryTotal) {
            throw new IllegalArgumentException("More pull requests change tests than there are pull requests: person "
                    + personTestedPullRequests.size() + " of " + personPullRequests.size() + ", repository "
                    + repositoryTested + " of " + repositoryTotal);
        }
    }

    long personTested() {
        return personTestedPullRequests.size();
    }

    long personTotal() {
        return personPullRequests.size();
    }
}
