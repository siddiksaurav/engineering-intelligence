package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Writes the links of a pull request to its work items (decision 60). */
@Component
public class PullRequestWorkItemLinks {

    private final PullRequestWorkItemLinkRepository links;
    private final Clock clock;

    PullRequestWorkItemLinks(PullRequestWorkItemLinkRepository links, Clock clock) {
        this.links = links;
        this.clock = clock;
    }

    /** A reference of a pull request, what the server made of it, and where it was found. */
    public record Found(ResolvedReference resolved, FoundIn foundIn) {}

    /**
     * Replaces every earlier link of the pull request with these, in one transaction. The links of
     * other pull requests are not touched.
     *
     * @param pullRequestExternalId the F02 external id, for example {@code opf/openproject#24204}
     */
    @Transactional
    public void replace(String pullRequestExternalId, List<Found> found) {
        links.deleteOfPullRequest(pullRequestExternalId);
        found.stream()
                .distinct()
                .map(each -> new PullRequestWorkItemLink(
                        pullRequestExternalId, each.resolved(), each.foundIn(), clock.instant()))
                .forEach(links::save);
    }
}
