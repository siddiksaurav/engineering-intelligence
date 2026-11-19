package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoredPullRequestsTest extends DatabaseTest {

    private static final String REPOSITORY = "opf/openproject";
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 14);

    @Autowired
    private RawRecordStore store;

    @Autowired
    private StoredPullRequests storedPullRequests;

    private void storePullRequest(String repository, int number, String mergedAt, String title) {
        String url = "https://github.com/" + repository + "/pull/" + number;
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.PULL_REQUEST.recordKind(),
                repository + "#" + number,
                url,
                CanonicalJson.parse(
                        "{\"number\":" + number + ",\"title\":\"" + title + "\",\"mergedAt\":\"" + mergedAt + "\"}"));
    }

    private static List<String> titles(List<RawRecord> records) {
        return records.stream()
                .map(record -> record.getPayload().get("title").asString())
                .toList();
    }

    @Test
    void listsTheLatestVersionOfEachPullRequestMergedInTheRange() {
        storePullRequest(REPOSITORY, 1, "2026-07-02T08:00:00Z", "first version");
        storePullRequest(REPOSITORY, 1, "2026-07-02T08:00:00Z", "second version");
        storePullRequest(REPOSITORY, 2, "2026-07-14T23:59:59Z", "last second of the range");
        storePullRequest(REPOSITORY, 3, "2026-07-15T00:00:00Z", "the day after");
        storePullRequest(REPOSITORY, 4, "2026-06-30T23:59:59Z", "the day before");
        storePullRequest("other/repository", 5, "2026-07-03T08:00:00Z", "another repository");

        List<RawRecord> found = storedPullRequests.mergedBetween(REPOSITORY, FROM, TO);

        assertEquals(List.of("second version", "last second of the range"), titles(found));
        assertEquals(List.of(2, 1), found.stream().map(RawRecord::getVersion).toList());
    }

    @Test
    void findsOnePullRequestByRepositoryAndNumber() {
        storePullRequest(REPOSITORY, 7, "2026-07-02T08:00:00Z", "first version");
        storePullRequest(REPOSITORY, 7, "2026-07-02T08:00:00Z", "second version");

        assertEquals(
                "second version",
                storedPullRequests
                        .find(REPOSITORY, 7)
                        .orElseThrow()
                        .getPayload()
                        .get("title")
                        .asString());
        assertTrue(storedPullRequests.find(REPOSITORY, 8).isEmpty());
    }
}
