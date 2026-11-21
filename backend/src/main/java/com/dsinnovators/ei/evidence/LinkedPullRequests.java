package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.BotAccounts;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.source.github.GitHubAuthor;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.openproject.PullRequestWorkItemLink;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads the stored pull requests the way both work builders need them: bots removed (decision
 * 102), the author mapped to a person, and the work items linked where a link is {@code resolved}
 * wherever it was found (decision 104).
 */
@Component
class LinkedPullRequests {

    static final String UNKNOWN_AUTHOR = "unknown author";

    private final StoredPullRequests storedPullRequests;
    private final StoredWorkPackages storedWorkPackages;
    private final BotAccounts botAccounts;
    private final PersonDirectory personDirectory;

    LinkedPullRequests(
            StoredPullRequests storedPullRequests,
            StoredWorkPackages storedWorkPackages,
            BotAccounts botAccounts,
            PersonDirectory personDirectory) {
        this.storedPullRequests = storedPullRequests;
        this.storedWorkPackages = storedWorkPackages;
        this.botAccounts = botAccounts;
        this.personDirectory = personDirectory;
    }

    /**
     * The pull requests that link a work item and are not by a bot, oldest merge first, and how
     * many pull requests of bots were left out; the bot list of {@code quarter} applies.
     */
    Read read(Quarter quarter) {
        List<PullRequestFacts> all =
                storedPullRequests.all().stream().map(PullRequestFacts::of).toList();
        List<PullRequestFacts> byHumans = all.stream()
                .filter(pullRequest -> !isByBot(pullRequest, quarter))
                .toList();
        List<LinkedPullRequest> linked =
                byHumans.stream().map(this::linked).flatMap(Optional::stream).toList();
        return new Read(linked, all.size() - byHumans.size());
    }

    private boolean isByBot(PullRequestFacts pullRequest, Quarter quarter) {
        return pullRequest
                .author()
                .filter(account -> botAccounts.isBot(account.login(), account.typename(), quarter))
                .isPresent();
    }

    private Optional<LinkedPullRequest> linked(PullRequestFacts pullRequest) {
        List<Long> workItems = storedWorkPackages.linksOf(pullRequest.externalId()).stream()
                .filter(PullRequestWorkItemLink::isVisible)
                .map(PullRequestWorkItemLink::getWorkPackageId)
                .distinct()
                .toList();
        if (workItems.isEmpty()) {
            return Optional.empty();
        }
        Optional<GitHubAuthor> author = pullRequest.author();
        return Optional.of(new LinkedPullRequest(
                pullRequest,
                author.map(GitHubAuthor::login).orElse(UNKNOWN_AUTHOR),
                author.flatMap(account -> personDirectory.personOfGitHubAccount(account.databaseId())),
                workItems));
    }

    /** What one read found. */
    record Read(List<LinkedPullRequest> pullRequests, int botPullRequestsSkipped) {}
}
