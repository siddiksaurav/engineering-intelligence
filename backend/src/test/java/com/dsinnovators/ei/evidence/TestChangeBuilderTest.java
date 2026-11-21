package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

class TestChangeBuilderTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");
    private static final String SPEC_FILE = "spec/models/work_package_spec.rb";
    private static final String CODE_FILE = "app/models/work_package.rb";

    @Autowired
    private TestChangeBuilder builder;

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

    @BeforeEach
    void storeTheDeveloper() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        digests = new StoredDigestsFixture(jdbc, judgments);
        ada = records.person(1, "ada");
        records.project(5);
    }

    private BuildResult build() {
        return builder.build(linkedPullRequestsReader.read(QUARTER).pullRequests(), QUARTER);
    }

    private void adaMergesAPullRequestWithADigest(int number, String testKind, String... changedFiles) {
        records.pullRequest(number, "ada", "User", 1, "2026-07-0" + (number + 1) + "T08:00:00Z");
        records.diff(number, changedFiles);
        digests.digest(number, StoredDigestsFixture.answer("fix", testKind));
    }

    private long rawRecordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }

    @Test
    void buildsOneItemForAPullRequestThatChangesASpecFile() {
        records.workPackage(102, "Bug", null, 5);
        adaMergesAPullRequestWithADigest(1, "regression", CODE_FILE, SPEC_FILE);
        records.link(1, 102);

        BuildResult result = build();

        assertEquals(List.of(), result.failures());
        assertEquals(1, result.items().size());
        BuiltItem item = result.items().getFirst();
        assertEquals("test_change:opf/openproject#1", item.key());
        assertEquals(EvidenceType.TEST_CHANGE, item.type());
        assertEquals(ada, item.personId());
        assertEquals(null, item.projectId());
        assertEquals(LocalDate.of(2026, 7, 2), item.occurredOn());
        assertEquals("https://github.com/opf/openproject/pull/1", item.sourceUrl());
        assertEquals(CanonicalJson.canonicalize(CanonicalJson.parse("""
                {"pullRequest":{"externalId":"opf/openproject#1","number":1,"title":"PR 1","url":"https://github.com/opf/openproject/pull/1","mergedAt":"2026-07-02T08:00:00Z"},
                 "linkedWorkItems":[{"number":102,"title":"Work item 102","type":"Bug","url":"https://community.openproject.org/wp/102"}],
                 "testKind":"regression",
                 "testFiles":["spec/models/work_package_spec.rb"]}
                """)), CanonicalJson.canonicalize(item.fields()));
        assertEquals(
                List.of(
                        new EvidenceItemSource(
                                rawRecordId("pull_request", "opf/openproject#1"), SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(
                                rawRecordId("pull_request_diff", "opf/openproject#1"), SourceRole.PULL_REQUEST_DIFF),
                        new EvidenceItemSource(rawRecordId("work_package", "102"), SourceRole.WORK_PACKAGE)),
                item.sources());
    }

    @Test
    void aPullRequestWithNoLinkAndNoWorkItemStillGivesATestChange() {
        adaMergesAPullRequestWithADigest(1, "unit", SPEC_FILE);
        records.pullRequest(2, "ada", "User", 1, "2026-07-03T08:00:00Z");
        records.diff(2, CODE_FILE);
        digests.digest(2, StoredDigestsFixture.answer("fix", "none"));
        records.linkNotVisible(1, "FND-1");

        List<String> keys = build().items().stream().map(BuiltItem::key).toList();

        assertEquals(List.of("test_change:opf/openproject#1"), keys);
    }

    @Test
    void aPullRequestWithoutADigestOrOfABotGivesNoItem() {
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.diff(1, SPEC_FILE);
        records.pullRequest(2, "dependabot[bot]", "Bot", 77, "2026-07-03T08:00:00Z");
        records.diff(2, SPEC_FILE);
        digests.digest(2, StoredDigestsFixture.answer("fix", "unit"));

        assertEquals(List.of(), build().items());
    }

    @Test
    void theTestFilesAreThoseThatMatchTheTestPathsSetting() {
        adaMergesAPullRequestWithADigest(
                1, "unit", "frontend/src/app/list.component.spec.ts", "lib/helper_spec.rb", "lib/helper.rb");

        List<String> testFiles = build().items()
                .getFirst()
                .fields()
                .path("testFiles")
                .valueStream()
                .map(file -> file.asString())
                .toList();

        assertEquals(List.of("frontend/src/app/list.component.spec.ts", "lib/helper_spec.rb"), testFiles);
    }

    @Test
    void anAuthorWithNoPersonFailsTheItemByItsKey() {
        records.pullRequest(1, "stranger", "User", 55, "2026-07-02T08:00:00Z");
        records.diff(1, SPEC_FILE);
        digests.digest(1, StoredDigestsFixture.answer("fix", "unit"));

        BuildResult result = build();

        assertEquals(List.of(), result.items());
        assertEquals(
                List.of(new BuilderFailure("test_change:opf/openproject#1", "no person for GitHub account stranger")),
                result.failures());
    }

    @Test
    void aNewDigestChangesTheContentHash() {
        adaMergesAPullRequestWithADigest(1, "unit", SPEC_FILE);
        String before = build().items().getFirst().contentHash();

        digests.digest(1, StoredDigestsFixture.answer("fix", "unit"));
        String after = build().items().getFirst().contentHash();

        assertNotEquals(before, after);
        assertTrue(after.length() == 64);
    }
}
