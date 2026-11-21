package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.judgment.PullRequestDigests.CurrentDigest;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * A stored pull request of a human author, with the work items it links, if any, and its digest, if
 * it has one yet.
 *
 * @param authorLogin the author's login, for naming a failure
 * @param personId the author's person; empty when the author has none yet
 * @param workItems the numbers of the work items it links as {@code resolved}, each once; none when
 *     it links none or only items the import could not see (decision 104)
 * @param digest the current digest (F06); empty until {@code pull-request-digest} has judged it
 */
record LinkedPullRequest(
        PullRequestFacts pullRequest,
        String authorLogin,
        Optional<Long> personId,
        List<Long> workItems,
        Optional<CurrentDigest> digest) {

    /** Names the author for grouping: the person when there is one, else the login. */
    String authorKey() {
        return personId.map(String::valueOf).orElse(authorLogin);
    }

    /** The day the pull request was merged, in UTC: the date of every item built from it (decision 150). */
    LocalDate mergeDay() {
        return pullRequest.mergedAt().atOffset(ZoneOffset.UTC).toLocalDate();
    }

    /** The failure of an item whose author has no person yet (decision 102). */
    BuilderFailure noPerson(String itemKey) {
        return new BuilderFailure(itemKey, "no person for GitHub account " + authorLogin);
    }
}
