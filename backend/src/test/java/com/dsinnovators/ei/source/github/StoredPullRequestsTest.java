package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    @Test
    void findByExternalIdGivesTheLatestVersionOfThatPullRequest() {
        storePullRequest(REPOSITORY, 7, "2026-07-02T08:00:00Z", "first version");
        storePullRequest(REPOSITORY, 7, "2026-07-02T08:00:00Z", "second version");

        assertEquals(
                "second version",
                storedPullRequests
                        .findByExternalId(REPOSITORY + "#7")
                        .orElseThrow()
                        .getPayload()
                        .get("title")
                        .asString());
        assertTrue(storedPullRequests.findByExternalId(REPOSITORY + "#8").isEmpty());
    }

    @Test
    void findByExternalIdRefusesAnIdWithoutANumber() {
        assertThrows(IllegalArgumentException.class, () -> storedPullRequests.findByExternalId("opf/openproject"));
        assertThrows(IllegalArgumentException.class, () -> storedPullRequests.findByExternalId("opf/openproject#x"));
    }

    @Test
    void allGivesTheLatestVersionOfEveryPullRequestInMergeOrder() {
        storePullRequest(REPOSITORY, 1, "2026-07-09T08:00:00Z", "later merge, first version");
        storePullRequest(REPOSITORY, 1, "2026-07-09T08:00:00Z", "later merge, second version");
        storePullRequest(REPOSITORY, 2, "2026-07-02T08:00:00Z", "earlier merge");
        storePullRequest("other/repository", 3, "2026-08-02T08:00:00Z", "another repository");

        assertEquals(
                List.of("earlier merge", "later merge, second version", "another repository"),
                titles(storedPullRequests.all()));
    }

    @Test
    void aPullRequestIsReadAsItsFacts() {
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.PULL_REQUEST.recordKind(),
                REPOSITORY + "#24153",
                "https://github.com/opf/openproject/pull/24153",
                CanonicalJson.parse("{\"number\":24153,\"title\":\"Add a panel\","
                        + "\"url\":\"https://github.com/opf/openproject/pull/24153\","
                        + "\"mergedAt\":\"2026-07-02T08:00:00Z\","
                        + "\"author\":{\"login\":\"dfriquet\",\"__typename\":\"User\",\"databaseId\":42}}"));
        store.store(
                PullRequestRecorder.DATA_SOURCE,
                GitHubRecordKind.PULL_REQUEST.recordKind(),
                REPOSITORY + "#24154",
                "https://github.com/opf/openproject/pull/24154",
                CanonicalJson.parse("{\"number\":24154,\"title\":\"By a deleted account\","
                        + "\"url\":\"https://github.com/opf/openproject/pull/24154\","
                        + "\"mergedAt\":\"2026-07-03T08:00:00Z\",\"author\":null}"));

        List<PullRequestFacts> facts =
                storedPullRequests.all().stream().map(PullRequestFacts::of).toList();

        PullRequestFacts first = facts.get(0);
        assertEquals(REPOSITORY + "#24153", first.externalId());
        assertEquals(24153, first.number());
        assertEquals("Add a panel", first.title());
        assertEquals("https://github.com/opf/openproject/pull/24153", first.url());
        assertEquals(Instant.parse("2026-07-02T08:00:00Z"), first.mergedAt());
        assertEquals(Optional.of(new GitHubAuthor("dfriquet", "User", 42)), first.author());
        assertEquals(Optional.empty(), facts.get(1).author());
    }
}
