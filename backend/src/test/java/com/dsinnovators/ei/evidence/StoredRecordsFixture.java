package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.GitHubAccount;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stores the GitHub and OpenProject records the evidence builders read, in the layouts F02 and F03 store. */
final class StoredRecordsFixture {

    static final String REPOSITORY = "opf/openproject";

    private final RawRecordStore rawRecords;
    private final JdbcTemplate jdbc;
    private final Persons persons;

    StoredRecordsFixture(RawRecordStore rawRecords, JdbcTemplate jdbc, Persons persons) {
        this.rawRecords = rawRecords;
        this.jdbc = jdbc;
        this.persons = persons;
    }

    /** A person for the GitHub account, as {@code identity-build} makes one. */
    long person(long databaseId, String login) {
        return persons.ensure(new GitHubAccount(databaseId, login, "User")).personId();
    }

    void pullRequest(int number, String login, String typename, long databaseId, String mergedAt) {
        String author =
                "{\"login\":\"" + login + "\",\"__typename\":\"" + typename + "\",\"databaseId\":" + databaseId + "}";
        storePullRequest(number, mergedAt, author);
    }

    void pullRequestWithoutAuthor(int number, String mergedAt) {
        storePullRequest(number, mergedAt, "null");
    }

    private void storePullRequest(int number, String mergedAt, String author) {
        rawRecords.store(
                "github",
                "pull_request",
                REPOSITORY + "#" + number,
                "https://github.com/" + REPOSITORY + "/pull/" + number,
                CanonicalJson.parse("{\"number\":" + number + ",\"title\":\"PR " + number
                        + "\",\"url\":\"https://github.com/"
                        + REPOSITORY + "/pull/" + number + "\",\"mergedAt\":\"" + mergedAt + "\",\"author\":" + author
                        + "}"));
    }

    /** The diff record of a stored pull request, as F02 stores it, one modified file per name. */
    void diff(int pullRequestNumber, String... fileNames) {
        String files = Arrays.stream(fileNames)
                .map(name -> "{\"filename\":\"" + name + "\",\"status\":\"modified\",\"patch\":\"@@ -1 +1 @@\"}")
                .collect(Collectors.joining(",", "[", "]"));
        rawRecords.store(
                "github",
                "pull_request_diff",
                REPOSITORY + "#" + pullRequestNumber,
                "https://github.com/" + REPOSITORY + "/pull/" + pullRequestNumber + "/files",
                CanonicalJson.parse("{\"files\":" + files + ",\"truncated\":false}"));
    }

    /** An inline review comment on a stored pull request, as F02 stores it. */
    void reviewComment(
            long databaseId,
            int pullRequestNumber,
            String login,
            String typename,
            long authorDatabaseId,
            boolean reply) {
        String author = "{\"login\":\"" + login + "\",\"__typename\":\"" + typename + "\",\"databaseId\":"
                + authorDatabaseId + "}";
        storeReviewComment(databaseId, pullRequestNumber, author, reply);
    }

    /** An inline review comment whose author GitHub no longer names, as a deleted account leaves it. */
    void reviewCommentWithoutAuthor(long databaseId, int pullRequestNumber) {
        storeReviewComment(databaseId, pullRequestNumber, "null", false);
    }

    private void storeReviewComment(long databaseId, int pullRequestNumber, String author, boolean reply) {
        String url = "https://github.com/" + REPOSITORY + "/pull/" + pullRequestNumber + "#discussion_r" + databaseId;
        rawRecords.store(
                "github",
                "review_comment",
                REPOSITORY + "#" + pullRequestNumber + "/review_comment/" + databaseId,
                url,
                CanonicalJson.parse("{\"databaseId\":" + databaseId + ",\"url\":\"" + url
                        + "\",\"path\":\"app/models/work_package.rb\",\"createdAt\":\"2026-06-20T10:00:00Z\","
                        + "\"replyTo\":" + (reply ? "{\"databaseId\":1}" : "null") + ",\"pullRequest\":{\"number\":"
                        + pullRequestNumber + ",\"repository\":{\"nameWithOwner\":\"" + REPOSITORY
                        + "\"}},\"author\":" + author + "}"));
    }

    /** The project row of an OpenProject project; returns its table id. */
    long project(long externalId) {
        return jdbc.queryForObject(
                """
                insert into project (data_source_id, external_id, identifier, name, url)
                values ((select id from data_source where key = 'openproject'), ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                String.valueOf(externalId),
                "project-" + externalId,
                "Project " + externalId,
                "https://community.openproject.org/projects/" + externalId);
    }

    void workPackage(long number, String type, Long parent, long projectExternalId, String... statusChanges) {
        String parentLink = parent == null ? "{\"href\":null}" : "{\"href\":\"/api/v3/work_packages/" + parent + "\"}";
        rawRecords.store(
                "openproject",
                "work_package",
                String.valueOf(number),
                "https://community.openproject.org/api/v3/work_packages/" + number,
                CanonicalJson.parse("{\"id\":" + number + ",\"subject\":\"Work item " + number + "\",\"_links\":{"
                        + "\"type\":{\"title\":\"" + type + "\"},\"parent\":" + parentLink
                        + ",\"priority\":{\"title\":\"Normal\"},"
                        + "\"project\":{\"href\":\"/api/v3/projects/" + projectExternalId + "\"}}}"));
        for (int index = 0; index < statusChanges.length; index += 2) {
            statusChange(number, statusChanges[index], statusChanges[index + 1]);
        }
    }

    private void statusChange(long number, String status, String createdAt) {
        String id = number + "-" + status + "-" + createdAt;
        rawRecords.store(
                "openproject",
                "work_package_activity",
                id,
                "https://community.openproject.org/api/v3/work_packages/" + number + "/activity",
                CanonicalJson.parse("{\"id\":\"" + id + "\",\"createdAt\":\"" + createdAt + "\",\"details\":[{\"html\":"
                        + "\"<strong>Status</strong> changed from <i>new</i> to <i>" + status + "</i>\"}],"
                        + "\"_links\":{\"workPackage\":{\"href\":\"/api/v3/work_packages/" + number + "\"}}}"));
    }

    void link(int pullRequestNumber, long workPackage) {
        jdbc.update("""
                insert into pull_request_work_item (pull_request_external_id, work_package_id, reference, found_in, state)
                values (?, ?, ?, 'pull_request_body', 'resolved')
                """, REPOSITORY + "#" + pullRequestNumber, workPackage, String.valueOf(workPackage));
    }

    void linkNotVisible(int pullRequestNumber, String reference) {
        jdbc.update("""
                insert into pull_request_work_item (pull_request_external_id, work_package_id, reference, found_in, state)
                values (?, null, ?, 'pull_request_body', 'not_visible')
                """, REPOSITORY + "#" + pullRequestNumber, reference);
    }
}
