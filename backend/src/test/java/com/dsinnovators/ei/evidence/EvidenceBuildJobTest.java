package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.judgment.JudgmentStore;
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

    @Autowired
    private JudgmentStore judgments;

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
    void recordsWithoutAnAuthorGiveNoItemAndDoNotFailTheRun() throws Exception {
        records.pullRequestWithoutAuthor(6, "2026-07-06T08:00:00Z");
        records.link(6, 101);
        records.reviewCommentWithoutAuthor(901, 1);

        JsonNode run = runJob();

        assertEquals("succeeded", run.get("status").stringValue());
        assertEquals(2, run.get("counts").get("records_without_author").longValue());
        assertEquals(3, run.get("counts").get("items_created").longValue());
        assertEquals(3, rowCount());
    }

    @Test
    void anItemWhoseRebuildFailsStaysCurrentAndTheRunFailsNamingIt() throws Exception {
        runJob();
        jdbc.update("delete from person_identity where external_id = '2'");

        JsonNode run = runJob();

        assertEquals("failed", run.get("status").stringValue());
        assertTrue(run.get("errorMessage").stringValue().contains("review_comment_written:900"), run.toString());
        assertTrue(
                currentKeys().contains("review_comment_written:900"),
                currentKeys().toString());
        assertEquals(0, run.get("counts").path("items_withdrawn").asLong(0L));
    }

    private List<String> currentTiers() {
        return jdbc.queryForList("""
                select i.item_key || '=' || j.tier from judgment j
                join evidence_item i on i.id = j.evidence_item_id
                where j.kind = 'item_tier'
                  and not exists (select 1 from judgment s where s.supersedes_id = j.id)
                  and not exists (select 1 from evidence_item n where n.supersedes_id = i.id)
                order by i.item_key
                """, String.class);
    }

    @Test
    void writesATierJudgmentForANewTestChange() throws Exception {
        StoredDigestsFixture digests = new StoredDigestsFixture(jdbc, judgments);
        records.diff(2, "spec/models/work_package_spec.rb");
        long digestId = digests.digest(2, StoredDigestsFixture.answer("fix", "regression"))
                .getId();

        JsonNode counts = runJob().get("counts");

        assertEquals(1, counts.get("test_change_created").longValue());
        assertEquals(1, counts.get("item_tiers_written").longValue());
        assertEquals(1, counts.get("pull_requests_without_digest").longValue());
        assertEquals(List.of("test_change:opf/openproject#2=T2"), currentTiers());
        assertEquals(
                digestId,
                jdbc.queryForObject("select based_on_judgment_id from judgment where kind = 'item_tier'", Long.class));
    }

    @Test
    void aSecondRunWritesNoSecondTierJudgmentAndANewDigestWritesANewOne() throws Exception {
        StoredDigestsFixture digests = new StoredDigestsFixture(jdbc, judgments);
        records.diff(2, "spec/models/work_package_spec.rb");
        digests.digest(2, StoredDigestsFixture.answer("fix", "unit"));
        runJob();

        JsonNode unchanged = runJob().get("counts");

        assertEquals(0, unchanged.path("item_tiers_written").asLong(0L));
        assertEquals(1, jdbc.queryForObject("select count(*) from judgment where kind = 'item_tier'", Integer.class));

        digests.digest(2, StoredDigestsFixture.answer("fix", "ci_gate"));
        JsonNode changed = runJob().get("counts");

        assertEquals(1, changed.get("test_change_superseded").longValue());
        assertEquals(1, changed.get("item_tiers_written").longValue());
        assertEquals(List.of("test_change:opf/openproject#2=T3"), currentTiers());
    }

    @Test
    void aRefactorPullRequestWithNoWorkItemGetsItsItemAndItsTier() throws Exception {
        StoredDigestsFixture digests = new StoredDigestsFixture(jdbc, judgments);
        records.pullRequest(7, "ada", "User", 1, "2026-07-06T08:00:00Z");
        records.diff(7, "app/list.rb");
        digests.digest(
                7,
                StoredDigestsFixture.withTier(
                        StoredDigestsFixture.answer("refactor", "none"), "T2", "A small refactor"));

        JsonNode run = runJob();

        assertEquals("succeeded", run.get("status").stringValue());
        assertEquals(1, run.get("counts").get("refactor_performance_created").longValue());
        assertEquals(List.of("refactor_performance:opf/openproject#7=T2"), currentTiers());
    }

    @Test
    void anArgumentIsRefused() throws Exception {
        HttpResponse<String> response = jobs.start("evidence-build", "{\"quarter\":\"2026-Q3\"}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("quarter"), response.body());
    }
}
