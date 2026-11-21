package com.dsinnovators.ei.source.github;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the authors of the stored GitHub records, from their latest versions (decision 89). */
@Component
public class StoredGitHubAccounts {

    private static final List<String> AUTHORED_RECORD_KINDS = List.of(
            GitHubRecordKind.PULL_REQUEST.recordKind(),
            GitHubRecordKind.REVIEW.recordKind(),
            GitHubRecordKind.REVIEW_COMMENT.recordKind(),
            GitHubRecordKind.PULL_REQUEST_COMMENT.recordKind());

    private final StoredAccountRecords records;

    StoredGitHubAccounts(StoredAccountRecords records) {
        this.records = records;
    }

    /**
     * Every account that wrote a pull request, a review, a review comment or a pull request
     * comment, once each. When one account appears with two logins, the login of the most recently
     * fetched record is given.
     */
    @Transactional(readOnly = true)
    public List<GitHubAccount> accounts() {
        return records.findAccounts(PullRequestRecorder.DATA_SOURCE, AUTHORED_RECORD_KINDS).stream()
                .map(row -> new GitHubAccount(row.getDatabaseId(), row.getLogin(), row.getTypename()))
                .toList();
    }

    /** The external ids of the stored pull requests this account wrote, such as {@code opf/openproject#26057}. */
    @Transactional(readOnly = true)
    public List<String> pullRequestsOf(long databaseId) {
        return records.findExternalIdsAuthoredBy(
                PullRequestRecorder.DATA_SOURCE, GitHubRecordKind.PULL_REQUEST.recordKind(), databaseId);
    }
}
