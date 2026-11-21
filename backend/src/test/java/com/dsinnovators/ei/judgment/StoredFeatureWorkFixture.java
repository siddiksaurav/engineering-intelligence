package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.evidence.BuiltItem;
import com.dsinnovators.ei.evidence.EvidenceItemSource;
import com.dsinnovators.ei.evidence.EvidenceItemStore;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.evidence.SourceRole;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stores what a Feature work judgment reads, in the layouts F02, F03, F05 and F06 store: pull
 * requests with their digests, work items and the Feature work item rows built from them.
 */
final class StoredFeatureWorkFixture {

    static final String REPOSITORY = "opf/openproject";

    private final JdbcTemplate jdbc;
    private final RawRecordStore rawRecords;
    private final JudgmentStore judgments;
    private final EvidenceItemStore items;
    private final FeatureWorkItems featureWorkItems;
    private final long person;
    private int digestsWritten;

    StoredFeatureWorkFixture(
            JdbcTemplate jdbc,
            RawRecordStore rawRecords,
            JudgmentStore judgments,
            EvidenceItemStore items,
            FeatureWorkItems featureWorkItems) {
        this.jdbc = jdbc;
        this.rawRecords = rawRecords;
        this.judgments = judgments;
        this.items = items;
        this.featureWorkItems = featureWorkItems;
        this.person = jdbc.queryForObject("insert into person (display_name) values ('Ada') returning id", Long.class);
    }

    void pullRequest(int number, String mergedAt) {
        String url = "https://github.com/" + REPOSITORY + "/pull/" + number;
        rawRecords.store(
                "github",
                "pull_request",
                REPOSITORY + "#" + number,
                url,
                CanonicalJson.parse(
                        "{\"number\":" + number + ",\"title\":\"Pull request " + number + "\",\"body\":\"Body "
                                + number + "\",\"url\":\"" + url + "\",\"mergedAt\":\"" + mergedAt
                                + "\",\"author\":{\"login\":\"ada\",\"__typename\":\"User\",\"databaseId\":1}}"));
    }

    /** The digest of the latest version of the pull request; a second one supersedes the first. */
    Judgment digest(int pullRequestNumber) {
        String externalId = REPOSITORY + "#" + pullRequestNumber;
        long recordId = rawRecordId("pull_request", externalId);
        digestsWritten++;
        return judgments.write(new NewJudgment(
                JudgmentKind.PULL_REQUEST_DIGEST,
                JudgmentSubject.pullRequestRecord(recordId),
                PullRequestDigest.fromAnswer(FakeJudgmentModel.aDigest(), false).toValue(),
                null,
                "The title names a crash that is now handled.",
                List.of(new JudgmentFact(
                        "Adds a regression spec", "https://github.com/" + REPOSITORY + "/pull/" + pullRequestNumber)),
                JudgedBy.MODEL,
                FakeJudgmentModel.MODEL,
                "pull-request-digest/v1",
                "digest-input-" + digestsWritten,
                null,
                judgments
                        .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, externalId)
                        .map(Judgment::getId)
                        .orElse(null)));
    }

    void workPackage(long number, String type, String description) {
        rawRecords.store(
                "openproject",
                "work_package",
                String.valueOf(number),
                "https://community.openproject.org/api/v3/work_packages/" + number,
                CanonicalJson.parse("{\"id\":" + number + ",\"subject\":\"Work item " + number + "\",\"description\":"
                        + "{\"raw\":\"" + description + "\"},\"_links\":{\"type\":{\"title\":\"" + type
                        + "\"},\"parent\":{\"href\":null},\"priority\":{\"title\":\"High\"}}}"));
    }

    /** Writes the Feature work item row of the Feature, its tasks and its pull requests and hands it over as the judgment reads it. */
    FeatureWorkToJudge item(long feature, List<Long> tasks, int... pullRequestNumbers) {
        String key = "feature_work:" + feature + ":" + person;
        List<EvidenceItemSource> sources = new ArrayList<>();
        StringBuilder pullRequests = new StringBuilder();
        for (int number : pullRequestNumbers) {
            sources.add(new EvidenceItemSource(
                    rawRecordId("pull_request", REPOSITORY + "#" + number), SourceRole.PULL_REQUEST));
            pullRequests
                    .append(pullRequests.isEmpty() ? "" : ",")
                    .append("{\"externalId\":\"")
                    .append(REPOSITORY)
                    .append('#')
                    .append(number)
                    .append("\"}");
        }
        StringBuilder taskFields = new StringBuilder();
        sources.add(
                new EvidenceItemSource(rawRecordId("work_package", String.valueOf(feature)), SourceRole.WORK_PACKAGE));
        for (long task : tasks) {
            sources.add(
                    new EvidenceItemSource(rawRecordId("work_package", String.valueOf(task)), SourceRole.WORK_PACKAGE));
            taskFields
                    .append(taskFields.isEmpty() ? "" : ",")
                    .append("{\"number\":")
                    .append(task)
                    .append('}');
        }
        items.write(new BuiltItem(
                key,
                EvidenceType.FEATURE_WORK,
                person,
                null,
                LocalDate.parse("2026-09-10"),
                "https://community.openproject.org/wp/" + feature,
                CanonicalJson.parse("{\"feature\":{\"number\":" + feature + "},\"tasks\":[" + taskFields
                        + "],\"pullRequests\":[" + pullRequests + "]}"),
                sources));
        return featureWorkItems.current().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private long rawRecordId(String recordKind, String externalId) {
        return jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = ? and external_id = ?",
                Long.class,
                recordKind,
                externalId);
    }
}
