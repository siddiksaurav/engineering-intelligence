package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.judgment.JudgmentStore;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

class WorkTypeItemBuilderTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");
    private static final String DONE_STATUS = "tested";
    private static final String DONE_AT = "2026-08-05T09:00:00.000Z";

    @Autowired
    private WorkTypeItemBuilder builder;

    @Autowired
    private LinkedPullRequests linkedPullRequestsReader;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    @Autowired
    private JudgmentStore judgments;

    private StoredRecordsFixture records;
    private StoredDigestsFixture digests;
    private long ada;
    private long projectId;

    @BeforeEach
    void storeTheDeveloperAndTheProject() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        digests = new StoredDigestsFixture(jdbc, judgments);
        ada = records.person(1, "ada");
        projectId = records.project(5);
    }

    private BuildResult build() {
        return builder.build(linkedPullRequestsReader.read(QUARTER).pullRequests(), QUARTER);
    }

    private List<String> builtKeys() {
        return build().items().stream().map(BuiltItem::key).toList();
    }

    private void adaMergesPullRequest(int number, ObjectNode digestAnswer) {
        records.pullRequest(number, "ada", "User", 1, "2026-07-%02dT08:00:00Z".formatted(number + 1));
        records.diff(number, "app/models/work_package.rb", "docs/guide.md");
        digests.digest(number, digestAnswer);
    }

    private static ObjectNode refactor() {
        return StoredDigestsFixture.withTier(
                StoredDigestsFixture.answer("refactor", "none"), "T2", "A small refactor on own initiative");
    }

    private long rawRecordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }

    @Test
    void aRefactorPullRequestWithNoWorkItemGivesARefactorItem() {
        adaMergesPullRequest(1, refactor());

        BuildResult result = build();

        assertEquals(List.of(), result.failures());
        assertEquals(1, result.items().size());
        BuiltItem item = result.items().getFirst();
        assertEquals("refactor_performance:opf/openproject#1", item.key());
        assertEquals(EvidenceType.REFACTOR_PERFORMANCE, item.type());
        assertEquals(ada, item.personId());
        assertEquals(null, item.projectId());
        assertEquals(LocalDate.of(2026, 7, 2), item.occurredOn());
        assertEquals("https://github.com/opf/openproject/pull/1", item.sourceUrl());
        assertEquals(CanonicalJson.canonicalize(CanonicalJson.parse("""
                {"pullRequest":{"externalId":"opf/openproject#1","number":1,"title":"PR 1","url":"https://github.com/opf/openproject/pull/1","mergedAt":"2026-07-02T08:00:00Z"},
                 "linkedWorkItems":[],
                 "workType":"refactor",
                 "modulesTouched":["core"],
                 "dependencyChanges":false}
                """)), CanonicalJson.canonicalize(item.fields()));
        assertEquals(
                List.of(
                        new EvidenceItemSource(
                                rawRecordId("pull_request", "opf/openproject#1"), SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(
                                rawRecordId("pull_request_diff", "opf/openproject#1"), SourceRole.PULL_REQUEST_DIFF)),
                item.sources());
        assertEquals(1, item.digests().size());
    }

    @Test
    void eachWorkTypeWithNoWorkItemGivesItsItemOrNone() {
        adaMergesPullRequest(1, StoredDigestsFixture.answer("feature", "none"));
        adaMergesPullRequest(2, StoredDigestsFixture.answer("enhancement", "none"));
        adaMergesPullRequest(3, StoredDigestsFixture.answer("fix", "none"));
        adaMergesPullRequest(
                4,
                StoredDigestsFixture.withTier(
                        StoredDigestsFixture.answer("performance", "none"), "T3", "Removes a measured bottleneck"));
        adaMergesPullRequest(
                5,
                StoredDigestsFixture.withTier(StoredDigestsFixture.answer("security", "none"), "T2", "Dependency fix"));
        adaMergesPullRequest(
                6, StoredDigestsFixture.withTier(StoredDigestsFixture.answer("infra", "none"), "T2", "Pipeline fix"));
        adaMergesPullRequest(7, StoredDigestsFixture.answer("docs", "none"));
        int number = 8;
        for (String type : List.of("maintenance", "research", "technical_task", "operational_support", "test")) {
            adaMergesPullRequest(number++, StoredDigestsFixture.answer(type, "none"));
        }

        assertEquals(
                List.of(
                        "feature_work:pull_request:opf/openproject#1",
                        "feature_work:pull_request:opf/openproject#2",
                        "bug_fix:pull_request:opf/openproject#3",
                        "refactor_performance:opf/openproject#4",
                        "security:opf/openproject#5",
                        "infra_release:opf/openproject#6",
                        "documentation:opf/openproject#7"),
                builtKeys());
    }

    @Test
    void theNoWorkItemFeatureWorkAndBugFixAndTheOtherItemsCarryTheirFields() {
        adaMergesPullRequest(1, StoredDigestsFixture.answer("feature", "none"));
        adaMergesPullRequest(2, StoredDigestsFixture.answer("fix", "none"));
        ObjectNode security =
                StoredDigestsFixture.withTier(StoredDigestsFixture.answer("security", "none"), "T2", "Dependency fix");
        security.put("securityKind", "dependency_upgrade");
        adaMergesPullRequest(3, security);
        ObjectNode infra =
                StoredDigestsFixture.withTier(StoredDigestsFixture.answer("infra", "none"), "T3", "Team pipeline");
        infra.put("infraKind", "pipeline").put("infraScope", "team");
        adaMergesPullRequest(4, infra);
        ObjectNode docs = StoredDigestsFixture.answer("docs", "none");
        docs.put("documentationScope", "team");
        adaMergesPullRequest(5, docs);

        List<BuiltItem> items = build().items();

        String pullRequest1 =
                "{\"externalId\":\"opf/openproject#%d\",\"number\":%d,\"title\":\"PR %d\",\"url\":\"https://github.com/opf/openproject/pull/%d\",\"mergedAt\":\"2026-07-0%dT08:00:00Z\"}";
        assertEquals(
                CanonicalJson.canonicalize(
                        CanonicalJson.parse("{\"noWorkItem\":true,\"workType\":\"feature\",\"pullRequests\":["
                                + pullRequest1.formatted(1, 1, 1, 1, 2) + "]}")),
                CanonicalJson.canonicalize(items.get(0).fields()));
        assertEquals(
                CanonicalJson.canonicalize(CanonicalJson.parse(
                        "{\"noWorkItem\":true,\"pullRequests\":[" + pullRequest1.formatted(2, 2, 2, 2, 3) + "]}")),
                CanonicalJson.canonicalize(items.get(1).fields()));
        assertEquals("dependency_upgrade", items.get(2).fields().path("kind").asString());
        assertTrue(items.get(2).fields().path("vulnerabilityReference").isNull());
        assertEquals("pipeline", items.get(3).fields().path("kind").asString());
        assertEquals("team", items.get(3).fields().path("scope").asString());
        assertEquals("team", items.get(4).fields().path("scope").asString());
        assertEquals(
                List.of("app/models/work_package.rb", "docs/guide.md"),
                items.get(4)
                        .fields()
                        .path("files")
                        .valueStream()
                        .map(file -> file.asString())
                        .toList());
    }

    @Test
    void aPullRequestWithOnlyAnInvisibleLinkIsHandledAsOneWithNoWorkItem() {
        adaMergesPullRequest(1, refactor());
        records.linkNotVisible(1, "FND-1");

        assertEquals(List.of("refactor_performance:opf/openproject#1"), builtKeys());
    }

    @Test
    void aLinkToAWorkItemWithNoFeatureAboveItFollowsTheNoWorkItemRulesAndNamesTheItem() {
        records.workPackage(200, "Code maintenance", null, 5);
        adaMergesPullRequest(1, refactor());
        records.link(1, 200);

        BuiltItem item = build().items().getFirst();

        assertEquals("refactor_performance:opf/openproject#1", item.key());
        assertEquals(
                200, item.fields().path("linkedWorkItems").get(0).path("number").asInt());
        assertTrue(item.sources()
                .contains(new EvidenceItemSource(rawRecordId("work_package", "200"), SourceRole.WORK_PACKAGE)));
    }

    @Test
    void aPullRequestUnderAFeatureGivesNothingNew() {
        records.workPackage(100, "Feature", null, 5, DONE_STATUS, DONE_AT);
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(200, "Code maintenance", null, 5);
        adaMergesPullRequest(1, refactor());
        records.link(1, 101);
        adaMergesPullRequest(2, refactor());
        records.link(2, 200);
        records.link(2, 101);

        assertEquals(List.of(), builtKeys());
    }

    @Test
    void aBugLinkKeepsOnlyTheBugFixAndItsOtherNonBugLinksFollowTheRules() {
        records.workPackage(102, "Bug", null, 5, DONE_STATUS, DONE_AT);
        records.workPackage(200, "Documentation", null, 5);
        adaMergesPullRequest(1, StoredDigestsFixture.answer("docs", "none"));
        records.link(1, 102);
        adaMergesPullRequest(2, StoredDigestsFixture.answer("docs", "none"));
        records.link(2, 102);
        records.link(2, 200);

        assertEquals(List.of("documentation:opf/openproject#2"), builtKeys());
    }

    @Test
    void aFixPullRequestWithAStandaloneLinkIsABugFixWithNoWorkItem() {
        records.workPackage(200, "Task", null, 5);
        adaMergesPullRequest(1, StoredDigestsFixture.answer("fix", "none"));
        records.link(1, 200);

        assertEquals(List.of("bug_fix:pull_request:opf/openproject#1"), builtKeys());
    }

    @Test
    void aFeaturePullRequestOnAStandaloneItemGivesFeatureWorkOfTheItemOnceItIsDone() {
        records.workPackage(200, "Task", null, 5, DONE_STATUS, DONE_AT);
        adaMergesPullRequest(1, StoredDigestsFixture.answer("feature", "none"));
        records.link(1, 200);
        adaMergesPullRequest(2, StoredDigestsFixture.answer("enhancement", "none"));
        records.link(2, 200);
        adaMergesPullRequest(3, StoredDigestsFixture.answer("fix", "none"));
        records.link(3, 200);

        List<BuiltItem> items = build().items();

        BuiltItem featureWork = items.stream()
                .filter(item -> item.type() == EvidenceType.FEATURE_WORK)
                .findFirst()
                .orElseThrow();
        assertEquals("feature_work:200:" + ada, featureWork.key());
        assertEquals(LocalDate.of(2026, 8, 5), featureWork.occurredOn());
        assertEquals(projectId, featureWork.projectId());
        assertEquals("https://community.openproject.org/wp/200", featureWork.sourceUrl());
        assertEquals(200, featureWork.fields().path("feature").path("number").asInt());
        assertEquals(
                200, featureWork.fields().path("tasks").get(0).path("number").asInt());
        assertEquals(
                List.of("opf/openproject#1", "opf/openproject#2"),
                featureWork
                        .fields()
                        .path("pullRequests")
                        .valueStream()
                        .map(pullRequest -> pullRequest.path("externalId").asString())
                        .toList());
        assertEquals("2026-08-05T09:00:00Z", featureWork.fields().path("doneAt").asString());
        assertEquals(2, featureWork.digests().size());
        assertEquals(
                List.of("bug_fix:pull_request:opf/openproject#3"),
                items.stream()
                        .filter(item -> item.type() == EvidenceType.BUG_FIX)
                        .map(BuiltItem::key)
                        .toList());
    }

    @Test
    void aStandaloneItemThatIsNotDoneGivesNoFeatureWork() {
        records.workPackage(200, "Task", null, 5);
        adaMergesPullRequest(1, StoredDigestsFixture.answer("feature", "none"));
        records.link(1, 200);

        assertEquals(List.of(), builtKeys());
    }

    @Test
    void aPullRequestWithoutADigestOrOfABotGivesNothing() {
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.diff(1, "app/a.rb");
        records.pullRequest(2, "dependabot[bot]", "Bot", 77, "2026-07-03T08:00:00Z");
        records.diff(2, "app/a.rb");
        digests.digest(2, refactor());

        assertEquals(List.of(), builtKeys());
    }

    @Test
    void anAuthorWithNoPersonFailsTheItemsByTheirKeys() {
        records.workPackage(200, "Task", null, 5, DONE_STATUS, DONE_AT);
        records.pullRequest(1, "stranger", "User", 55, "2026-07-02T08:00:00Z");
        records.diff(1, "app/a.rb");
        digests.digest(1, refactor());
        records.pullRequest(2, "stranger", "User", 55, "2026-07-03T08:00:00Z");
        records.diff(2, "app/a.rb");
        digests.digest(2, StoredDigestsFixture.answer("feature", "none"));
        records.link(2, 200);

        BuildResult result = build();

        assertEquals(List.of(), result.items());
        assertEquals(
                List.of(
                        new BuilderFailure(
                                "refactor_performance:opf/openproject#1", "no person for GitHub account stranger"),
                        new BuilderFailure("feature_work:200:stranger", "no person for GitHub account stranger")),
                result.failures());
    }

    @Test
    void aPullRequestWithADigestButNoDiffRecordFailsItsItem() {
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        digests.digest(1, refactor());

        BuildResult result = build();

        assertEquals(List.of(), result.items());
        assertEquals(
                List.of(new BuilderFailure(
                        "refactor_performance:opf/openproject#1", "no diff record is stored for opf/openproject#1")),
                result.failures());
    }
}
