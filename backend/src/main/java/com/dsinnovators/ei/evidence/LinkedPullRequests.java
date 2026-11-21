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
     * many pull requests of bots and how many without a GitHub author were left out (decision 126);
     * the bot list of {@code quarter} applies.
     */
    Read read(Quarter quarter) {
        List<PullRequestFacts> all =
                storedPullRequests.all().stream().map(PullRequestFacts::of).toList();
        List<AuthoredPullRequest> authored = all.stream()
                .flatMap(pullRequest ->
                        pullRequest.author().map(author -> new AuthoredPullRequest(pullRequest, author)).stream())
                .toList();
        List<AuthoredPullRequest> byHumans = authored.stream()
                .filter(pullRequest -> !isBot(pullRequest.author(), quarter))
                .toList();
        List<LinkedPullRequest> linked =
                byHumans.stream().map(this::linked).flatMap(Optional::stream).toList();
        return new Read(linked, all.size(), authored.size() - byHumans.size(), all.size() - authored.size());
    }

    private boolean isBot(GitHubAuthor author, Quarter quarter) {
        return botAccounts.isBot(author.login(), author.typename(), quarter);
    }

    private Optional<LinkedPullRequest> linked(AuthoredPullRequest authored) {
        PullRequestFacts pullRequest = authored.pullRequest();
        List<Long> workItems = storedWorkPackages.linksOf(pullRequest.externalId()).stream()
                .filter(PullRequestWorkItemLink::isVisible)
                .map(PullRequestWorkItemLink::getWorkPackageId)
                .distinct()
                .toList();
        if (workItems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LinkedPullRequest(
                pullRequest,
                authored.author().login(),
                personDirectory.personOfGitHubAccount(authored.author().databaseId()),
                workItems));
    }

    private record AuthoredPullRequest(PullRequestFacts pullRequest, GitHubAuthor author) {}

    /** What one read found. */
    record Read(
            List<LinkedPullRequest> pullRequests,
            int pullRequestsRead,
            int botPullRequestsSkipped,
            int pullRequestsWithoutAuthor) {}
}
