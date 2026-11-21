package com.dsinnovators.ei.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.identity.Persons;
import com.dsinnovators.ei.judgment.FeatureWorkToJudge;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeatureWorkItemsForJudgmentTest extends DatabaseTest {

    @Autowired
    private FeatureWorkItemsForJudgment featureWorkItems;

    @Autowired
    private EvidenceItemStore itemStore;

    @Autowired
    private EvidenceItemRepository items;

    @Autowired
    private RawRecordStore rawRecords;

    @Autowired
    private Persons persons;

    private StoredRecordsFixture records;
    private long ada;

    @BeforeEach
    void storeTheRecordsTheItemsRestOn() {
        records = new StoredRecordsFixture(rawRecords, jdbc, persons);
        ada = records.person(1, "ada");
        records.project(5);
        records.pullRequest(1, "ada", "User", 1, "2026-07-02T08:00:00Z");
        records.pullRequest(2, "ada", "User", 1, "2026-07-03T08:00:00Z");
        records.workPackage(100, "Feature", null, 5);
        records.workPackage(101, "Implementation", 100L, 5);
        records.workPackage(102, "Implementation", 100L, 5);
    }

    private long recordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }

    private BuiltItem featureWork(String key, String fieldsJson, List<EvidenceItemSource> sources) {
        return new BuiltItem(
                key,
                EvidenceType.FEATURE_WORK,
                ada,
                null,
                LocalDate.parse("2026-09-10"),
                "https://community.openproject.org/wp/100",
                CanonicalJson.parse(fieldsJson),
                sources);
    }

    private static String fieldsOf(String tasks, String pullRequestNumbers) {
        return "{\"feature\":{\"number\":100,\"title\":\"Work item 100\",\"type\":\"Feature\",\"url\":\"u\"},"
                + "\"tasks\":" + tasks + ",\"pullRequests\":" + pullRequestNumbers
                + ",\"doneAt\":\"2026-09-10T08:30:00Z\"}";
    }

    private static String task(int number) {
        return "{\"number\":" + number + ",\"title\":\"t\",\"type\":\"Implementation\",\"url\":\"u\"}";
    }

    private static String pullRequest(int number) {
        return "{\"externalId\":\"opf/openproject#" + number + "\",\"number\":" + number
                + ",\"title\":\"PR\",\"url\":\"u\",\"mergedAt\":\"2026-07-02T08:00:00Z\"}";
    }

    @Test
    void handsOverEveryCurrentFeatureWorkItemWithItsTasksAndPullRequests() {
        long firstPullRequest = recordId("pull_request", "opf/openproject#1");
        long secondPullRequest = recordId("pull_request", "opf/openproject#2");
        long feature = recordId("work_package", "100");
        long task = recordId("work_package", "101");
        long otherTask = recordId("work_package", "102");
        itemStore.write(featureWork(
                "feature_work:100:" + ada,
                fieldsOf(
                        "[" + task(100) + "," + task(101) + "," + task(102) + "]",
                        "[" + pullRequest(1) + "," + pullRequest(2) + "]"),
                List.of(
                        new EvidenceItemSource(firstPullRequest, SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(secondPullRequest, SourceRole.PULL_REQUEST),
                        new EvidenceItemSource(feature, SourceRole.WORK_PACKAGE),
                        new EvidenceItemSource(task, SourceRole.WORK_PACKAGE),
                        new EvidenceItemSource(otherTask, SourceRole.WORK_PACKAGE))));
        itemStore.write(new BuiltItem(
                "feature_work:pull_request:opf/openproject#2",
                EvidenceType.FEATURE_WORK,
                ada,
                null,
                LocalDate.parse("2026-07-03"),
                "https://github.com/opf/openproject/pull/2",
                CanonicalJson.parse(
                        "{\"noWorkItem\":true,\"pullRequests\":[" + pullRequest(2) + "],\"workType\":\"feature\"}"),
                List.of(new EvidenceItemSource(secondPullRequest, SourceRole.PULL_REQUEST))));
        itemStore.write(new BuiltItem(
                "bug_fix:pull_request:opf/openproject#1",
                EvidenceType.BUG_FIX,
                ada,
                null,
                LocalDate.parse("2026-07-02"),
                "https://github.com/opf/openproject/pull/1",
                CanonicalJson.parse("{\"noWorkItem\":true,\"pullRequests\":[" + pullRequest(1) + "]}"),
                List.of(new EvidenceItemSource(firstPullRequest, SourceRole.PULL_REQUEST))));

        List<FeatureWorkToJudge> handedOver = featureWorkItems.current();

        long featureRow =
                items.findCurrentByKey("feature_work:100:" + ada).orElseThrow().getId();
        long pullRequestRow = items.findCurrentByKey("feature_work:pull_request:opf/openproject#2")
                .orElseThrow()
                .getId();
        assertEquals(
                List.of(
                        new FeatureWorkToJudge(
                                featureRow,
                                "feature_work:100:" + ada,
                                "https://community.openproject.org/wp/100",
                                List.of(firstPullRequest, secondPullRequest, feature, task, otherTask).stream()
                                        .sorted()
                                        .toList(),
                                Optional.of(100L),
                                List.of(101L, 102L),
                                List.of("opf/openproject#1", "opf/openproject#2")),
                        new FeatureWorkToJudge(
                                pullRequestRow,
                                "feature_work:pull_request:opf/openproject#2",
                                "https://github.com/opf/openproject/pull/2",
                                List.of(secondPullRequest),
                                Optional.empty(),
                                List.of(),
                                List.of("opf/openproject#2"))),
                handedOver);
    }

    @Test
    void leavesOutTheWithdrawnAndTheSupersededRows() {
        long pullRequestRecord = recordId("pull_request", "opf/openproject#1");
        List<EvidenceItemSource> sources = List.of(new EvidenceItemSource(pullRequestRecord, SourceRole.PULL_REQUEST));
        itemStore.write(featureWork(
                "feature_work:100:" + ada, fieldsOf("[" + task(101) + "]", "[" + pullRequest(1) + "]"), sources));
        itemStore.write(featureWork(
                "feature_work:100:" + ada,
                fieldsOf("[" + task(101) + "," + task(102) + "]", "[" + pullRequest(1) + "]"),
                sources));
        itemStore.write(featureWork(
                "feature_work:200:" + ada, fieldsOf("[" + task(101) + "]", "[" + pullRequest(1) + "]"), sources));
        itemStore.withdraw("feature_work:200:" + ada);

        List<FeatureWorkToJudge> handedOver = featureWorkItems.current();

        assertEquals(1, handedOver.size());
        assertEquals("feature_work:100:" + ada, handedOver.getFirst().key());
        assertEquals(List.of(101L, 102L), handedOver.getFirst().taskNumbers());
        assertEquals(
                items.findCurrentByKey("feature_work:100:" + ada).orElseThrow().getId(),
                handedOver.getFirst().itemRowId());
    }
}
