package com.dsinnovators.ei.judgment;

import static com.dsinnovators.ei.judgment.NewJudgmentBuilder.aDigestOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class PullRequestDigestsTest extends DatabaseTest {

    private static final String PULL_REQUEST = "opf/openproject#1";

    @Autowired
    private PullRequestDigests digests;

    @Autowired
    private JudgmentStore store;

    private static JsonNode digestValue(boolean judgedOnPartialCode) {
        return PullRequestDigest.fromAnswer(FakeJudgmentModel.aDigest(), judgedOnPartialCode)
                .toValue();
    }

    // A new record version is a new subject, so the digest names the one it replaces, as the job does.
    private long storeDigestOfVersion(int version, String inputHash, boolean judgedOnPartialCode, Long supersedesId) {
        JudgmentSubject subject =
                JudgmentSubject.pullRequestRecord(JudgmentSubjectRows.insertPullRequestRecord(jdbc, version));
        NewJudgmentBuilder digest =
                aDigestOf(subject).value(digestValue(judgedOnPartialCode)).inputHash(inputHash);
        if (supersedesId != null) {
            digest.supersedes(supersedesId);
        }
        return store.write(digest.build()).getId();
    }

    @Test
    void returnsOnlyTheCurrentDigestOfAPullRequest() {
        long first = storeDigestOfVersion(1, "first", false, null);
        long current = storeDigestOfVersion(2, "second", true, first);

        Optional<PullRequestDigests.CurrentDigest> found = digests.currentOf(PULL_REQUEST);

        assertTrue(found.isPresent());
        assertEquals(current, found.get().judgmentId());
        assertTrue(found.get().digest().judgedOnPartialCode());
        assertEquals(PullRequestDigest.WorkType.FIX, found.get().digest().workType());
        assertEquals(PullRequestDigest.TestKind.REGRESSION, found.get().digest().testKind());
    }

    @Test
    void returnsNothingForAPullRequestWithoutADigest() {
        assertFalse(digests.currentOf("opf/openproject#2").isPresent());
    }
}
