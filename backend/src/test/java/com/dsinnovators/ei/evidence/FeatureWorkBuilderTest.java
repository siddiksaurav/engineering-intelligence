package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureWorkBuilderTest extends DatabaseTest {

    private static final String DONE_STATUS = "tested";
    private static final String FEATURE_DONE_AT = "2026-09-10T08:30:00.000Z";

    @Autowired
    private FeatureWorkBuilder builder;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    private StoredRecordsFixture records;
    private long ada;
    private long projectId;

    @BeforeEach
    void storeTheDevelopersAndTheProject() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        ada = records.person(1, "ada");
        projectId = records.project(5);
    }

    private void adaMergesPullRequest(int number, String mergedAt) {
        records.pullRequest(number, "ada", "User", 1, mergedAt);
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
    void anImplementationUnderAFeatureJoinsItsAuthorsGroup() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);

        BuildResult result = builder.build();

        assertEquals(List.of(), result.failures());
        assertEquals(1, result.items().size());
        BuiltItem item = result.items().get(0);
        assertEquals("feature_work:100:" + ada, item.key());
        assertEquals(EvidenceType.FEATURE_WORK, item.type());
        assertEquals(ada, item.personId());
        assertEquals(projectId, item.projectId());
        assertEquals(LocalDate.of(2026, 9, 10), item.occurredOn());
        assertEquals("https://community.openproject.org/wp/100", item.sourceUrl());
        assertEquals(canonical("""
                {"feature":{"number":100,"title":"Work item 100","type":"Feature","url":"https://community.openproject.org/wp/100"},
                 "tasks":[{"number":101,"title":"Work item 101","type":"Implementation","url":"https://community.openproject.org/wp/101"}],
                 "pullRequests":[{"externalId":"opf/openproject#1","number":1,"title":"PR 1","url":"https://github.com/opf/openproject/pull/1","mergedAt":"2026-07-02T08:00:00Z"}],
                 "doneAt":"2026-09-10T08:30:00Z"}
                """), CanonicalJson.canonicalize(item.fields()));
        assertEquals(
                List.of(
                        new EvidenceItemSource(
                                rawRecordId("pull_request", "opf/openproject#1"), SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(rawRecordId("work_package", "100"), SourceRole.WORK_PACKAGE),
                        new EvidenceItemSource(rawRecordId("work_package", "101"), SourceRole.WORK_PACKAGE)),
                item.sources());
    }

    @Test
    void aBugUnderTheFeatureStaysOutOfTheGroup() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(102, "Bug", 100L, 5);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        records.link(1, 102);
        adaMergesPullRequest(2, "2026-07-03T08:00:00Z");
        records.link(2, 102);

        BuiltItem item = builder.build().items().get(0);

        assertEquals(List.of(101L), taskNumbers(item));
        assertEquals(List.of("opf/openproject#1"), pullRequestIds(item));
        assertTrue(item.sources().stream()
                .noneMatch(source -> source.rawRecordId() == rawRecordId("work_package", "102")));
    }

    @Test
    void aPullRequestThatLinksTheFeatureItselfPutsTheFeatureAmongTheTasks() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 100);

        assertEquals(List.of(100L), taskNumbers(builder.build().items().get(0)));
    }

    @Test
    void aPullRequestWithTwoFeaturesJoinsTheGroupOfEach() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        records.workPackage(200, "Feature", null, 5, DONE_STATUS, "2026-09-12T08:30:00.000Z");
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(201, "Implementation", 200L, 5);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        records.link(1, 201);

        List<BuiltItem> items = builder.build().items();

        assertEquals(List.of("feature_work:100:" + ada, "feature_work:200:" + ada), keys(items));
        assertEquals(List.of("opf/openproject#1"), pullRequestIds(items.get(0)));
        assertEquals(List.of("opf/openproject#1"), pullRequestIds(items.get(1)));
        assertEquals(List.of(101L), taskNumbers(items.get(0)));
        assertEquals(List.of(201L), taskNumbers(items.get(1)));
    }

    @Test
    void aWorkItemWithNoFeatureAncestorGivesNothing() {
        records.workPackage(300, "Implementation", null, 5);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 300);

        assertEquals(List.of(), builder.build().items());
    }

    @Test
    void aNotVisibleLinkAndAPullRequestWithoutLinksGiveNothing() {
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.linkNotVisible(1, "FND-1");
        adaMergesPullRequest(2, "2026-07-03T08:00:00Z");

        BuildResult result = builder.build();

        assertEquals(List.of(), result.items());
        assertEquals(List.of(), result.failures());
    }

    @Test
    void aFeatureThatIsNotDoneYetGivesNothing() {
        records.workPackage(100, "Feature", null, 5, "in development", FEATURE_DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);

        assertEquals(List.of(), builder.build().items());
    }

    @Test
    void theDateIsTheFirstDoneDayOfTheFeatureReadInUtc() {
        records.workPackage(
                100, "Feature", null, 5, "closed", "2026-09-20T10:00:00.000Z", DONE_STATUS, "2026-09-10T23:59:59.000Z");
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 100);

        BuiltItem item = builder.build().items().get(0);

        assertEquals(LocalDate.of(2026, 9, 10), item.occurredOn());
        assertEquals("2026-09-10T23:59:59Z", item.fields().path("doneAt").asString());
    }

    @Test
    void twoAuthorsOnOneFeatureGiveTwoGroups() {
        long grace = records.person(2, "grace");
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(102, "Implementation", 100L, 5);
        adaMergesPullRequest(1, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        records.pullRequest(2, "grace", "User", 2, "2026-07-03T08:00:00Z");
        records.link(2, 102);

        List<BuiltItem> items = builder.build().items();

        assertEquals(List.of("feature_work:100:" + ada, "feature_work:100:" + grace), keys(items));
        assertEquals(List.of(101L), taskNumbers(items.get(0)));
        assertEquals(List.of(102L), taskNumbers(items.get(1)));
    }

    @Test
    void aBotAuthorGivesNothingAndIsNotAFailure() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        records.pullRequest(1, "dependabot", "Bot", 99, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        records.pullRequest(2, "openprojectci", "User", 98, "2026-07-03T08:00:00Z");
        records.link(2, 101);

        BuildResult result = builder.build();

        assertEquals(List.of(), result.items());
        assertEquals(List.of(), result.failures());
    }

    @Test
    void aHumanAuthorWithNoPersonFailsThatItemOnly() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, FEATURE_DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        records.pullRequest(1, "stranger", "User", 77, "2026-07-02T08:00:00Z");
        records.link(1, 101);
        adaMergesPullRequest(2, "2026-07-03T08:00:00Z");
        records.link(2, 101);

        BuildResult result = builder.build();

        assertEquals(List.of("feature_work:100:" + ada), keys(result.items()));
        assertEquals(1, result.failures().size());
        assertTrue(
                result.failures().get(0).startsWith("feature_work:100:stranger"),
                result.failures().get(0));
    }

    private static List<String> keys(List<BuiltItem> items) {
        return items.stream().map(BuiltItem::key).toList();
    }

    private static List<Long> taskNumbers(BuiltItem item) {
        return item.fields()
                .path("tasks")
                .valueStream()
                .map(task -> task.path("number").asLong())
                .toList();
    }

    private static List<String> pullRequestIds(BuiltItem item) {
        return item.fields()
                .path("pullRequests")
                .valueStream()
                .map(pullRequest -> pullRequest.path("externalId").asString())
                .toList();
    }
}
