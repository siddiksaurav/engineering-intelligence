package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecordStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoredGitHubAccountsTest extends DatabaseTest {

    private static final String PULL_REQUEST_1 = "opf/openproject#1";
    private static final String PULL_REQUEST_2 = "opf/openproject#2";

    @Autowired
    private RawRecordStore store;

    @Autowired
    private StoredGitHubAccounts storedAccounts;

    private void store(GitHubRecordKind kind, String externalId, long databaseId, String login, String typename) {
        String payload = "{\"author\":{\"login\":\"%s\",\"__typename\":\"%s\",\"databaseId\":%d}}"
                .formatted(login, typename, databaseId);
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                kind.recordKind(),
                externalId,
                "https://github.com/" + externalId,
                CanonicalJson.parse(payload));
    }

    @Test
    void findsEachAccountOnceAcrossPullRequestsReviewsAndComments() {
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_1, 10, "author", "User");
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_2, 11, "dependabot", "Bot");
        store(GitHubRecordKind.REVIEW, PULL_REQUEST_1 + "/review/1", 12, "reviewer-only", "User");
        store(GitHubRecordKind.REVIEW_COMMENT, PULL_REQUEST_1 + "/review_comment/1", 10, "author", "User");
        store(GitHubRecordKind.PULL_REQUEST_COMMENT, PULL_REQUEST_1 + "/comment/1", 13, "commenter", "User");
        store(GitHubRecordKind.REVIEW, PULL_REQUEST_2 + "/review/2", 12, "reviewer-only", "User");

        assertEquals(
                List.of(
                        new GitHubAccount(10, "author", "User"),
                        new GitHubAccount(11, "dependabot", "Bot"),
                        new GitHubAccount(12, "reviewer-only", "User"),
                        new GitHubAccount(13, "commenter", "User")),
                storedAccounts.accounts());
    }

    @Test
    void aRenamedLoginGivesTheLoginOfTheNewerRecord() {
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_1, 10, "old-login", "User");
        store(GitHubRecordKind.REVIEW, PULL_REQUEST_2 + "/review/1", 10, "new-login", "User");

        assertEquals(List.of(new GitHubAccount(10, "new-login", "User")), storedAccounts.accounts());
    }

    @Test
    void onlyTheLatestVersionOfARecordCounts() {
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_1, 10, "first-author", "User");
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_1, 14, "second-author", "User");

        assertEquals(List.of(new GitHubAccount(14, "second-author", "User")), storedAccounts.accounts());
    }

    @Test
    void aRecordWithoutAuthorAndRecordsOfOtherKindsAreIgnored() {
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.PULL_REQUEST.recordKind(),
                PULL_REQUEST_1,
                "https://github.com/" + PULL_REQUEST_1,
                CanonicalJson.parse("{\"author\":null}"));
        store(GitHubRecordKind.PULL_REQUEST_DIFF, PULL_REQUEST_2 + "/diff", 15, "diff-author", "User");

        assertTrue(storedAccounts.accounts().isEmpty());
    }

    @Test
    void givesTheLatestStoredPullRequestsOfOneAccount() {
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_1, 10, "author", "User");
        store(GitHubRecordKind.PULL_REQUEST, PULL_REQUEST_2, 10, "author", "User");
        store(GitHubRecordKind.PULL_REQUEST, "opf/openproject#3", 11, "other", "User");
        store(GitHubRecordKind.REVIEW, "opf/openproject#3/review/1", 10, "author", "User");
        store(GitHubRecordKind.PULL_REQUEST, "opf/openproject#4", 10, "author", "User");
        store(GitHubRecordKind.PULL_REQUEST, "opf/openproject#4", 11, "other", "User");

        assertEquals(List.of(PULL_REQUEST_1, PULL_REQUEST_2), storedAccounts.pullRequestsOf(10));
        assertTrue(storedAccounts.pullRequestsOf(99).isEmpty());
    }
}
