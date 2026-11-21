package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.BotAccounts;
import com.dsinnovators.ei.identity.PersonDirectory;
import com.dsinnovators.ei.source.github.GitHubAuthor;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.openproject.PullRequestWorkItemLink;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;

    LinkedPullRequests(
            StoredPullRequests storedPullRequests,
            StoredWorkPackages storedWorkPackages,
            BotAccounts botAccounts,
            PersonDirectory personDirectory,
            Clock clock) {
        this.storedPullRequests = storedPullRequests;
        this.storedWorkPackages = storedWorkPackages;
        this.botAccounts = botAccounts;
        this.personDirectory = personDirectory;
        this.clock = clock;
    }

    /** The pull requests that link a work item and are not by a bot, oldest merge first. */
    List<LinkedPullRequest> read() {
        Quarter quarter = Quarter.of(LocalDate.now(clock));
        return storedPullRequests.all().stream()
                .map(PullRequestFacts::of)
                .map(pullRequest -> linked(pullRequest, quarter))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<LinkedPullRequest> linked(PullRequestFacts pullRequest, Quarter quarter) {
        List<Long> workItems = storedWorkPackages.linksOf(pullRequest.externalId()).stream()
                .filter(PullRequestWorkItemLink::isVisible)
                .map(PullRequestWorkItemLink::getWorkPackageId)
                .distinct()
                .toList();
        Optional<GitHubAuthor> author = pullRequest.author();
        boolean byBot = author.filter(account -> botAccounts.isBot(account.login(), account.typename(), quarter))
                .isPresent();
        if (workItems.isEmpty() || byBot) {
            return Optional.empty();
        }
        return Optional.of(new LinkedPullRequest(
                pullRequest,
                author.map(GitHubAuthor::login).orElse(UNKNOWN_AUTHOR),
                author.flatMap(account -> personDirectory.personOfGitHubAccount(account.databaseId())),
                workItems));
    }
}
