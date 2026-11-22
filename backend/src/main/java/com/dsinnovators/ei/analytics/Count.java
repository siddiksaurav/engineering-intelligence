package com.dsinnovators.ei.analytics;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * One count of an analytic with what it was counted from, so that every number can be followed to
 * its rows (decision 199).
 *
 * @param count the number counted: items for an item count, pull requests for a pull request count
 * @param evidenceItemIds the ids of the counted evidence items; empty for a count of pull requests
 * @param pullRequestNumbers the numbers of the pull requests counted, or the ones the counted items rest on
 */
public record Count(long count, List<Long> evidenceItemIds, List<Integer> pullRequestNumbers) {

    public Count {
        evidenceItemIds = List.copyOf(evidenceItemIds);
        pullRequestNumbers = List.copyOf(pullRequestNumbers);
    }

    public static Count none() {
        return new Count(0, List.of(), List.of());
    }

    public static Count ofPullRequests(Collection<Integer> pullRequestNumbers) {
        return new Count(pullRequestNumbers.size(), List.of(), List.copyOf(pullRequestNumbers));
    }

    /** The two counts together; an item or a pull request in both is listed once. */
    public Count plus(Count other) {
        TreeSet<Long> itemIds = new TreeSet<>(evidenceItemIds);
        itemIds.addAll(other.evidenceItemIds);
        TreeSet<Integer> numbers = new TreeSet<>(pullRequestNumbers);
        numbers.addAll(other.pullRequestNumbers);
        return new Count(count + other.count, List.copyOf(itemIds), List.copyOf(numbers));
    }
}
