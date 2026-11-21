package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.source.github.PullRequestFacts;
import java.util.List;
import java.util.Optional;

/**
 * A stored pull request of a human author that links at least one work item.
 *
 * @param authorLogin the author's login, for naming a failure; a fixed text when GitHub names no author
 * @param personId the author's person; empty when the author has none yet or GitHub names none
 * @param workItems the numbers of the work items it links, each once
 */
record LinkedPullRequest(
        PullRequestFacts pullRequest, String authorLogin, Optional<Long> personId, List<Long> workItems) {

    /** Names the author for grouping: the person when there is one, else the login. */
    String authorKey() {
        return personId.map(String::valueOf).orElse(authorLogin);
    }
}
