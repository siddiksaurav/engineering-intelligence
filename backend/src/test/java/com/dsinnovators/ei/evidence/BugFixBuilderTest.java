package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BugFixBuilderTest extends DatabaseTest {

    private static final Quarter DONE_STATUSES_QUARTER = Quarter.parse("2026-Q3");
    private static final String DONE_STATUS = "tested";
    private static final String BUG_DONE_AT = "2026-08-05T09:00:00.000Z";

    @Autowired
    private BugFixBuilder bugFixBuilder;

    @Autowired
    private FeatureWorkBuilder featureWorkBuilder;

    @Autowired
    private LinkedPullRequests linkedPullRequestsReader;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    private StoredRecordsFixture records;
    private long ada;
    private long projectId;

    @BeforeEach
    void storeTheDeveloperAndTheProject() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        ada = records.person(1, "ada");
        projectId = records.project(5);
    }

    private void adaMergesPullRequest(int number, String mergedAt) {
        records.pullRequest(number, "ada", "User", 1, mergedAt);
    }

    private List<LinkedPullRequest> linkedPullRequests() {
        return linkedPullRequestsReader.read(DONE_STATUSES_QUARTER).pullRequests();
    }

    private long rawRecordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }

    private static String canonical(String json) {
        return CanonicalJson.canonicalize(CanonicalJson.parse(json));
    }

    @Test
    void aBugUnderAFeatureIsABugFixWithItsFeatureNamed() {
        records.workPackage(100, "Feature", null, 5);
        records.workPackage(102, "Bug", 100L, 5, DONE_STATUS, BUG_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 102);
        adaMergesPullRequest(2, "2026-07-03T08:00:00Z");
        records.link(2, 102);

        BuildResult result = bugFixBuilder.build(linkedPullRequests(), DONE_STATUSES_QUARTER);

        assertEquals(List.of(), result.failures());
        assertEquals(1, result.items().size());
        BuiltItem item = result.items().get(0);
        assertEquals("bug_fix:102:" + ada, item.key());
        assertEquals(EvidenceType.BUG_FIX, item.type());
        assertEquals(ada, item.personId());
        assertEquals(projectId, item.projectId());
        assertEquals(LocalDate.of(2026, 8, 5), item.occurredOn());
        assertEquals("https://community.openproject.org/wp/102", item.sourceUrl());
        assertEquals(canonical("""
                {"bug":{"number":102,"title":"Work item 102","url":"https://community.openproject.org/wp/102","priority":"Normal"},
                 "underFeature":100,
                 "pullRequests":[
                   {"externalId":"opf/openproject#1","number":1,"title":"PR 1","url":"https://github.com/opf/openproject/pull/1","mergedAt":"2026-07-02T08:00:00Z"},
                   {"externalId":"opf/openproject#2","number":2,"title":"PR 2","url":"https://github.com/opf/openproject/pull/2","mergedAt":"2026-07-03T08:00:00Z"}],
                 "doneAt":"2026-08-05T09:00:00Z"}
                """), CanonicalJson.canonicalize(item.fields()));
        assertEquals(
                List.of(
                        new EvidenceItemSource(
                                rawRecordId("pull_request", "opf/openproject#1"), SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(
                                rawRecordId("pull_request", "opf/openproject#2"), SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(rawRecordId("work_package", "102"), SourceRole.WORK_PACKAGE)),
                item.sources());
    }

    @Test
    void aBugWithNoFeatureAboveItNamesNoFeature() {
        records.workPackage(102, "Bug", null, 5, DONE_STATUS, BUG_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 102);

        BuiltItem item = bugFixBuilder
                .build(linkedPullRequests(), DONE_STATUSES_QUARTER)
                .items()
                .get(0);

        assertTrue(item.fields().path("underFeature").isNull());
    }

    @Test
    void aPullRequestWithABugAndAnImplementationAppearsInBothBuildersOutput() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, "2026-09-10T08:30:00.000Z");
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(102, "Bug", 100L, 5, DONE_STATUS, BUG_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        records.link(1, 102);

        List<BuiltItem> bugFixes =
                bugFixBuilder.build(linkedPullRequests(), DONE_STATUSES_QUARTER).items();
        List<BuiltItem> featureWork = featureWorkBuilder
                .build(linkedPullRequests(), DONE_STATUSES_QUARTER)
                .items();

        assertEquals(
                List.of("bug_fix:102:" + ada),
                bugFixes.stream().map(BuiltItem::key).toList());
        assertEquals(
                List.of("feature_work:100:" + ada),
                featureWork.stream().map(BuiltItem::key).toList());
        assertEquals(
                "opf/openproject#1",
                bugFixes.get(0)
                        .fields()
                        .path("pullRequests")
                        .get(0)
                        .path("externalId")
                        .asString());
        assertEquals(
                "opf/openproject#1",
                featureWork
                        .get(0)
                        .fields()
                        .path("pullRequests")
                        .get(0)
                        .path("externalId")
                        .asString());
    }

    @Test
    void aBugThatIsNotDoneGivesNothing() {
        records.workPackage(102, "Bug", null, 5, "in development", BUG_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 102);

        assertEquals(
                List.of(),
                bugFixBuilder.build(linkedPullRequests(), DONE_STATUSES_QUARTER).items());
    }

    @Test
    void aWorkItemThatIsNotABugGivesNothing() {
        records.workPackage(101, "Implementation", null, 5, DONE_STATUS, BUG_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);

        assertEquals(
                List.of(),
                bugFixBuilder.build(linkedPullRequests(), DONE_STATUSES_QUARTER).items());
    }

    @Test
    void twoAuthorsOfOneBugGiveTwoItems() {
        long grace = records.person(2, "grace");
        records.workPackage(102, "Bug", null, 5, DONE_STATUS, BUG_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 102);
        records.pullRequest(2, "grace", "User", 2, "2026-07-03T08:00:00Z");
        records.link(2, 102);

        List<BuiltItem> items =
                bugFixBuilder.build(linkedPullRequests(), DONE_STATUSES_QUARTER).items();

        assertEquals(
                List.of("bug_fix:102:" + ada, "bug_fix:102:" + grace),
                items.stream().map(BuiltItem::key).toList());
        assertEquals(1, items.get(0).fields().path("pullRequests").size());
        assertEquals(1, items.get(1).fields().path("pullRequests").size());
    }

    @Test
    void aBotAuthorGivesNothingAndAHumanWithNoPersonFailsThatItem() {
        records.workPackage(102, "Bug", null, 5, DONE_STATUS, BUG_DONE_AT);
        records.pullRequest(1, "dependabot", "Bot", 99, "2026-07-02T08:00:00Z");
        records.link(1, 102);
        records.pullRequest(2, "stranger", "User", 77, "2026-07-03T08:00:00Z");
        records.link(2, 102);

        BuildResult result = bugFixBuilder.build(linkedPullRequests(), DONE_STATUSES_QUARTER);

        assertEquals(List.of(), result.items());
        assertEquals(1, result.failures().size());
        assertTrue(
                result.failures().get(0).startsWith("bug_fix:102:stranger"),
                result.failures().get(0));
    }
}
