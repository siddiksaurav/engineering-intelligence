package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.judgment.JudgedBy;
import com.dsinnovators.ei.judgment.Judgment;
import com.dsinnovators.ei.judgment.JudgmentFact;
import com.dsinnovators.ei.judgment.JudgmentKind;
import com.dsinnovators.ei.judgment.JudgmentStore;
import com.dsinnovators.ei.judgment.JudgmentSubject;
import com.dsinnovators.ei.judgment.NewJudgment;
import com.dsinnovators.ei.judgment.PullRequestDigest;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.ObjectNode;

/** Stores the Stage 1 digests of stored pull requests, as the job {@code pull-request-digest} stores them. */
final class StoredDigestsFixture {

    static final String MODEL = "fake-model-1";
    static final String PROMPT_VERSION = "pull-request-digest/v1";

    private final JdbcTemplate jdbc;
    private final JudgmentStore judgments;
    private int written;

    StoredDigestsFixture(JdbcTemplate jdbc, JudgmentStore judgments) {
        this.jdbc = jdbc;
        this.judgments = judgments;
    }

    /** A valid digest answer of a pull request that is no refactor, security or infra; a test changes what it is about. */
    static ObjectNode answer(String workType, String testKind) {
        return (ObjectNode) CanonicalJson.parse("""
                {
                  "workType": "%s",
                  "modulesTouched": ["core"],
                  "schemaOrDataMigration": false,
                  "newIntegration": false,
                  "natureOfChange": "Changes how the list handles an empty date.",
                  "testsIncluded": true,
                  "testKind": "%s",
                  "workTypeTier": {"tier": "not_applicable", "reason": ""},
                  "securityKind": "not_applicable",
                  "infraKind": "not_applicable",
                  "infraScope": "not_applicable",
                  "dependencyChanges": false,
                  "documentationScope": "not_applicable",
                  "reason": "The title and the files show the change.",
                  "facts": [{"fact": "Changes the list", "filePath": "app/list.rb"}]
                }
                """.formatted(workType, testKind));
    }

    /** The same answer with the tier a refactor, performance, security or infra work type comes with. */
    static ObjectNode withTier(ObjectNode answer, String tier, String reason) {
        answer.putObject("workTypeTier").put("tier", tier).put("reason", reason);
        return answer;
    }

    /** Stores the digest of the latest version of the pull request; it supersedes the digest the pull request had. */
    Judgment digest(int pullRequestNumber, ObjectNode answer) {
        String externalId = StoredRecordsFixture.REPOSITORY + "#" + pullRequestNumber;
        long recordId = jdbc.queryForObject(
                "select max(id) from raw_record where record_kind = 'pull_request' and external_id = ?",
                Long.class,
                externalId);
        Long supersedesId = judgments
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, externalId)
                .map(Judgment::getId)
                .orElse(null);
        PullRequestDigest digest = PullRequestDigest.fromAnswer(answer, false);
        written++;
        return judgments.write(new NewJudgment(
                JudgmentKind.PULL_REQUEST_DIGEST,
                JudgmentSubject.pullRequestRecord(recordId),
                digest.toValue(),
                null,
                digest.reason(),
                List.of(new JudgmentFact(
                        "Changes the list",
                        "https://github.com/" + StoredRecordsFixture.REPOSITORY + "/pull/" + pullRequestNumber
                                + "/files")),
                null,
                JudgedBy.MODEL,
                MODEL,
                PROMPT_VERSION,
                "digest-input-" + written,
                null,
                supersedesId));
    }
}
