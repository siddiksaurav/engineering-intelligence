package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.source.RawRecordStore;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

/** {@code evidence-build} started over HTTP, as a person would, on stored GitHub and OpenProject records. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvidenceBuildJobTest extends DatabaseTest {

    private static final String NO_ARGUMENTS = "{}";
    private static final String DONE_STATUS = "tested";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    private JobEndpointClient jobs;
    private StoredRecordsFixture records;
    private long ada;

    @BeforeEach
    void storeAFeatureAFixAndAReviewComment() {
        jobs = new JobEndpointClient(port);
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        ada = records.person(1, "ada");
        records.person(2, "grace");
        records.project(5);
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, "2026-09-10T08:30:00.000Z");
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(102, "Bug", null, 5, DONE_STATUS, "2026-08-05T09:00:00.000Z");
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        records.pullRequest(2, "ada", "User", 1, "2026-07-03T08:00:00Z");
        records.link(2, 102);
        records.reviewComment(900, 1, "grace", "User", 2, false);
        records.pullRequest(3, "dependabot[bot]", "Bot", 77, "2026-07-04T08:00:00Z");
    }

    private JsonNode runJob() throws Exception {
        return jobs.awaitEnd(jobs.startAccepted("evidence-build", NO_ARGUMENTS));
    }

    private long rowCount() {
        return jdbc.queryForObject("select count(*) from evidence_item", Long.class);
    }

    private List<String> currentKeys() {
        return jdbc.queryForList("""
                select item_key from evidence_item e
                where not withdrawn
                  and not exists (select 1 from evidence_item newer where newer.supersedes_id = e.id)
                order by item_key
                """, String.class);
    }

    @Test
    void aSecondRunWritesNoRow() throws Exception {
        JsonNode first = runJob();

        assertEquals("succeeded", first.get("status").stringValue());
        JsonNode firstCounts = first.get("counts");
        assertEquals(3, firstCounts.get("items_created").longValue());
        assertEquals(1, firstCounts.get("feature_work_created").longValue());
        assertEquals(1, firstCounts.get("bug_fix_created").longValue());
        assertEquals(1, firstCounts.get("review_comment_written_created").longValue());
        assertEquals(3, firstCounts.get("pull_requests_read").longValue());
        assertEquals(1, firstCounts.get("bot_records_skipped").longValue());
        assertEquals(3, rowCount());

        JsonNode secondCounts = runJob().get("counts");

        assertEquals(3, secondCounts.get("items_unchanged").longValue());
        assertEquals(0, secondCounts.path("items_created").asLong(0L));
        assertEquals(0, secondCounts.path("items_superseded").asLong(0L));
        assertEquals(0, secondCounts.path("items_withdrawn").asLong(0L));
        assertEquals(3, rowCount());
    }

    @Test
    void aRemovedLinkWithdrawsTheGroup() throws Exception {
        runJob();
        jdbc.update("delete from pull_request_work_item where work_package_id = 101");

        JsonNode counts = runJob().get("counts");

        assertEquals(1, counts.get("items_withdrawn").longValue());
        assertEquals(1, counts.get("feature_work_withdrawn").longValue());
        assertEquals(List.of("bug_fix:102:" + ada, "review_comment_written:900"), currentKeys());
    }

    @Test
    void aNewPullRequestSupersedesTheGroup() throws Exception {
        runJob();
        records.pullRequest(4, "ada", "User", 1, "2026-07-05T08:00:00Z");
        records.link(4, 101);

        JsonNode counts = runJob().get("counts");

        assertEquals(1, counts.get("items_superseded").longValue());
        assertEquals(1, counts.get("feature_work_superseded").longValue());
        assertEquals(2, counts.get("items_unchanged").longValue());
        assertEquals(4, rowCount());
    }

    @Test
    void anAuthorWithNoPersonFailsTheRunAtTheEndWhileTheOtherItemsAreWritten() throws Exception {
        records.pullRequest(5, "stranger", "User", 55, "2026-07-06T08:00:00Z");
        records.link(5, 101);

        JsonNode run = runJob();

        assertEquals("failed", run.get("status").stringValue());
        assertTrue(run.get("errorMessage").stringValue().contains("feature_work:100:stranger"), run.toString());
        assertEquals(1, run.get("counts").get("items_failed").longValue());
        assertEquals(3, run.get("counts").get("items_created").longValue());
        assertEquals(3, rowCount());
    }

    @Test
    void anArgumentIsRefused() throws Exception {
        HttpResponse<String> response = jobs.start("evidence-build", "{\"quarter\":\"2026-Q3\"}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("quarter"), response.body());
    }
}
