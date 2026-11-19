package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.StoreResult.Outcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class PullRequestRecorderTest extends DatabaseTest {

    private static final String PR_URL = "https://github.com/opf/openproject/pull/7";

    @Autowired
    private PullRequestRecorder recorder;

    private static JsonNode json(String text) {
        return CanonicalJson.parse(text);
    }

    private static FetchedPullRequest pullRequest(String body, List<JsonNode> pullRequestComments) {
        return new FetchedPullRequest(
                "opf/openproject",
                7,
                PR_URL,
                json("{\"number\":7,\"body\":\"" + body + "\",\"files\":[{\"path\":\"a.rb\"}],\"labels\":[]}"),
                List.of(json("{\"databaseId\":100,\"url\":\"" + PR_URL
                        + "#pullrequestreview-100\",\"state\":\"APPROVED\"}")),
                List.of(
                        json("{\"databaseId\":200,\"url\":\"" + PR_URL + "#discussion_r200\",\"body\":\"why?\"}"),
                        json("{\"databaseId\":201,\"url\":\"" + PR_URL + "#discussion_r201\",\"body\":\"because\"}")),
                pullRequestComments,
                List.of(json("{\"filename\":\"a.rb\",\"patch\":\"@@ -0,0 +1 @@\"}")),
                false);
    }

    private static List<JsonNode> oneConversationComment() {
        return List.of(json("{\"databaseId\":300,\"url\":\"" + PR_URL + "#issuecomment-300\",\"body\":\"thanks\"}"));
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList(
                "select record_kind, external_id, version, url from raw_record order by record_kind, external_id, version");
    }

    private static Map<String, Object> row(String kind, String externalId, int version, String url) {
        return Map.of("record_kind", kind, "external_id", externalId, "version", version, "url", url);
    }

    @Test
    void aPullRequestIsStoredAsOneRowPerPart() {
        List<StoredRecord> stored = recorder.store(pullRequest("first", oneConversationComment()));

        assertEquals(
                List.of(
                        row("pull_request", "opf/openproject#7", 1, PR_URL),
                        row("pull_request_comment", "300", 1, PR_URL + "#issuecomment-300"),
                        row("pull_request_diff", "opf/openproject#7", 1, PR_URL + "/files"),
                        row("review", "100", 1, PR_URL + "#pullrequestreview-100"),
                        row("review_comment", "200", 1, PR_URL + "#discussion_r200"),
                        row("review_comment", "201", 1, PR_URL + "#discussion_r201")),
                rows());
        assertEquals(6, stored.size());
        assertEquals(
                List.of(Outcome.CREATED),
                stored.stream().map(StoredRecord::outcome).distinct().toList());
        assertEquals(
                json("{\"files\":[{\"filename\":\"a.rb\",\"patch\":\"@@ -0,0 +1 @@\"}],\"truncated\":false}"),
                json(jdbc.queryForObject(
                        "select payload::text from raw_record where record_kind = 'pull_request_diff'", String.class)));
    }

    @Test
    void thePullRequestStoredAgainIsUnchangedEverywhere() {
        recorder.store(pullRequest("first", oneConversationComment()));

        List<StoredRecord> again = recorder.store(pullRequest("first", oneConversationComment()));

        assertEquals(
                List.of(Outcome.UNCHANGED),
                again.stream().map(StoredRecord::outcome).distinct().toList());
        assertEquals(6, rows().size());
    }

    @Test
    void anEditedBodyMakesANewVersionOfThePullRequestRowOnly() {
        recorder.store(pullRequest("first", oneConversationComment()));

        List<StoredRecord> again = recorder.store(pullRequest("edited", oneConversationComment()));

        assertEquals(new StoredRecord(GitHubRecordKind.PULL_REQUEST, Outcome.NEW_VERSION), again.get(0));
        assertEquals(
                1,
                again.stream()
                        .filter(record -> record.outcome() == Outcome.NEW_VERSION)
                        .count());
        assertEquals(
                List.of("first", "edited"),
                jdbc.queryForList(
                        "select payload->>'body' from raw_record where record_kind = 'pull_request' order by version",
                        String.class));
    }

    @Test
    void aNewConversationCommentAddsOneRow() {
        recorder.store(pullRequest("first", List.of()));

        List<StoredRecord> again = recorder.store(pullRequest("first", oneConversationComment()));

        assertEquals(
                List.of(new StoredRecord(GitHubRecordKind.PULL_REQUEST_COMMENT, Outcome.CREATED)),
                again.stream()
                        .filter(record -> record.outcome() != Outcome.UNCHANGED)
                        .toList());
        assertEquals(6, rows().size());
    }

    @Test
    void aPartWithoutAnIdIsRefusedAndNothingOfThePullRequestIsStored() {
        FetchedPullRequest broken = new FetchedPullRequest(
                "opf/openproject",
                7,
                PR_URL,
                json("{\"number\":7}"),
                List.of(json("{\"url\":\"" + PR_URL + "#pullrequestreview-100\"}")),
                List.of(),
                List.of(),
                List.of(),
                false);

        GitHubApiException refused = assertThrows(GitHubApiException.class, () -> recorder.store(broken));

        assertEquals(
                "A review of opf/openproject#7 has no databaseId: {\"url\":\"" + PR_URL + "#pullrequestreview-100\"}",
                refused.getMessage());
        assertEquals(List.of(), rows());
    }
}
