package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** {@code github-import} started over HTTP, as a person would, with GitHub's answers recorded. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecordedGitHub.class)
class GitHubImportEndToEndTest extends DatabaseTest {

    private static final String IMPORT_24204 = "{\"repository\":\"opf/openproject\",\"pullRequestNumbers\":\"24204\"}";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private GitHubApiFixture github;

    private JobEndpointClient jobs;

    @BeforeEach
    void startClean() {
        jobs = new JobEndpointClient(port);
        github.server.reset();
    }

    private JsonNode importPullRequest24204() throws Exception {
        JsonNode run = jobs.awaitEnd(jobs.startAccepted("github-import", IMPORT_24204));
        github.server.verify();
        github.server.reset();
        return run;
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void anImportStartedOverHttpStoresEveryPartAndRecordsTheRun() throws Exception {
        RecordedPullRequest24204.expect(github.server);

        JsonNode run = importPullRequest24204();

        assertEquals("succeeded", run.get("status").stringValue());
        JsonNode counts = run.get("counts");
        assertEquals(1, counts.get("pull_requests_listed").longValue());
        assertEquals(1, counts.get("pull_request.created").longValue());
        assertEquals(1, counts.get("pull_request_diff.created").longValue());
        assertEquals(
                RecordedPullRequest24204.REVIEWS, counts.get("review.created").longValue());
        assertEquals(
                RecordedPullRequest24204.REVIEW_COMMENTS,
                counts.get("review_comment.created").longValue());
        assertEquals(
                RecordedPullRequest24204.PULL_REQUEST_COMMENTS,
                counts.get("pull_request_comment.created").longValue());
        assertEquals(
                2
                        + RecordedPullRequest24204.REVIEWS
                        + RecordedPullRequest24204.REVIEW_COMMENTS
                        + RecordedPullRequest24204.PULL_REQUEST_COMMENTS,
                count("select count(*) from raw_record"));
        assertEquals(
                RecordedPullRequest24204.FILES,
                count(
                        "select jsonb_array_length(payload->'files') from raw_record where record_kind = 'pull_request_diff'"));
    }

    @Test
    void aSecondRunAddsNoRows() throws Exception {
        RecordedPullRequest24204.expect(github.server);
        importPullRequest24204();
        long rowsAfterFirstRun = count("select count(*) from raw_record");
        RecordedPullRequest24204.expect(github.server);

        JsonNode second = importPullRequest24204();

        assertEquals("succeeded", second.get("status").stringValue());
        assertEquals(rowsAfterFirstRun, count("select count(*) from raw_record"));
        second.get("counts").properties().forEach(count -> {
            if (count.getKey().endsWith(".created") || count.getKey().endsWith(".new_version")) {
                throw new AssertionError("The second run wrote " + count.getKey() + " = " + count.getValue());
            }
        });
    }

    @Test
    void aChangedPullRequestIsANewVersionAndTheOldOneIsKept() throws Exception {
        RecordedPullRequest24204.expect(github.server);
        importPullRequest24204();
        String firstBody = jdbc.queryForObject(
                "select payload->>'body' from raw_record where record_kind = 'pull_request'", String.class);
        RecordedPullRequest24204.expectWithBody(github.server, "An edited description");

        JsonNode second = importPullRequest24204();

        assertEquals(1, second.get("counts").get("pull_request.new_version").longValue());
        assertEquals(
                List.of(firstBody, "An edited description"),
                jdbc.queryForList(
                        "select payload->>'body' from raw_record where record_kind = 'pull_request' order by version",
                        String.class));
    }
}
