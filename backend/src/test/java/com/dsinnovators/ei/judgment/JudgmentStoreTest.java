package com.dsinnovators.ei.judgment;

import static com.dsinnovators.ei.judgment.NewJudgmentBuilder.FACT;
import static com.dsinnovators.ei.judgment.NewJudgmentBuilder.aDigestOf;
import static com.dsinnovators.ei.judgment.NewJudgmentBuilder.anItemTierOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JudgmentStoreTest extends DatabaseTest {

    @Autowired
    private JudgmentStore store;

    private JudgmentSubject pullRequest;
    private JudgmentSubject evidenceItem;

    @BeforeEach
    void insertSubjects() {
        pullRequest = JudgmentSubject.pullRequestRecord(JudgmentSubjectRows.insertPullRequestRecord(jdbc, 1));
        evidenceItem = JudgmentSubject.evidenceItem(JudgmentSubjectRows.insertEvidenceItem(jdbc));
    }

    private int storedJudgments() {
        return jdbc.queryForObject("select count(*) from judgment", Integer.class);
    }

    @Test
    void refusesAFactWithoutALink() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(aDigestOf(pullRequest)
                        .facts(List.of(new JudgmentFact("Adds a spec", " ")))
                        .build()));
        assertEquals(0, storedJudgments());
    }

    @Test
    void refusesABlankReason() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(aDigestOf(pullRequest).reason(" ").build()));
    }

    @Test
    void refusesAJudgmentWithoutAFact() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(aDigestOf(pullRequest).facts(List.of()).build()));
    }

    @Test
    void refusesAModelJudgmentWithoutAModelOrAPromptVersion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(aDigestOf(pullRequest).model(" ").build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(aDigestOf(pullRequest).promptVersion(null).build()));
    }

    @Test
    void refusesATierOutsideT1ToT4() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(anItemTierOf(evidenceItem).tier("T5").build()));
    }

    @Test
    void refusesAKindOnTheWrongSubject() {
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(aDigestOf(evidenceItem).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.write(anItemTierOf(pullRequest).build()));
    }

    @Test
    void writesAJudgmentAndReadsItBack() {
        Judgment written =
                store.write(aDigestOf(pullRequest).inputHash("hash-1").build());

        Judgment read =
                store.findCurrent(JudgmentKind.PULL_REQUEST_DIGEST, pullRequest).orElseThrow();
        assertEquals(written.getId(), read.getId());
        assertEquals("Fixes a defect", read.getReason());
        assertEquals("{\"workType\":\"fix\"}", read.getValue().toString());
        assertEquals(List.of(FACT), read.getFacts());
        assertEquals("openai/gpt-oss-120b", read.getModel());
        assertEquals("pull-request-digest/v1", read.getPromptVersion());
        assertEquals("hash-1", read.getInputHash());
        assertNull(read.getSupersedesId());
    }

    @Test
    void refusesAModelJudgmentWhoseInputHashWasJudgedBefore() {
        store.write(aDigestOf(pullRequest).inputHash("same").build());

        assertThrows(
                IllegalStateException.class,
                () -> store.write(aDigestOf(pullRequest).inputHash("same").build()));
        assertEquals(1, storedJudgments());
    }

    @Test
    void tellsWhetherAModelJudgmentOfAnInputHashExists() {
        assertFalse(store.hasModelJudgment(JudgmentKind.PULL_REQUEST_DIGEST, "h"));

        store.write(aDigestOf(pullRequest).inputHash("h").build());

        assertTrue(store.hasModelJudgment(JudgmentKind.PULL_REQUEST_DIGEST, "h"));
        assertFalse(store.hasModelJudgment(JudgmentKind.ITEM_TIER, "h"));
    }

    @Test
    void aNewJudgmentOfTheSameSubjectAndKindSupersedesTheCurrentOne() {
        Judgment first = store.write(aDigestOf(pullRequest).inputHash("one").build());

        Judgment second = store.write(aDigestOf(pullRequest).inputHash("two").build());

        assertEquals(first.getId(), second.getSupersedesId());
        assertEquals(
                second.getId(),
                store.findCurrent(JudgmentKind.PULL_REQUEST_DIGEST, pullRequest)
                        .orElseThrow()
                        .getId());
    }

    @Test
    void aJudgmentOfAnotherSubjectIsNotSuperseded() {
        store.write(aDigestOf(pullRequest).inputHash("one").build());
        JudgmentSubject otherVersion =
                JudgmentSubject.pullRequestRecord(JudgmentSubjectRows.insertPullRequestRecord(jdbc, 2));

        Judgment other = store.write(aDigestOf(otherVersion).inputHash("two").build());

        assertNull(other.getSupersedesId());
    }

    @Test
    void findsTheCurrentJudgmentOfAnEvidenceItem() {
        Judgment tier = store.write(anItemTierOf(evidenceItem).tier("T2").build());

        Judgment read = store.findCurrent(JudgmentKind.ITEM_TIER, evidenceItem).orElseThrow();

        assertEquals(tier.getId(), read.getId());
        assertEquals("T2", read.getTier());
        assertTrue(
                store.findCurrent(JudgmentKind.PULL_REQUEST_DIGEST, pullRequest).isEmpty());
    }

    @Test
    void keepsTheDigestAnItemTierWasBasedOn() {
        Judgment digest = store.write(aDigestOf(pullRequest).build());

        Judgment tier = store.write(anItemTierOf(evidenceItem)
                .inputHash("tier-hash")
                .basedOn(digest.getId())
                .build());

        assertEquals(digest.getId(), tier.getBasedOnJudgmentId());
    }

    @Test
    void aJudgmentSupersedesTheOneItNamesEvenOnAnotherSubject() {
        Judgment olderVersion =
                store.write(aDigestOf(pullRequest).inputHash("one").build());
        JudgmentSubject newerVersion =
                JudgmentSubject.pullRequestRecord(JudgmentSubjectRows.insertPullRequestRecord(jdbc, 2));

        Judgment newer = store.write(aDigestOf(newerVersion)
                .inputHash("two")
                .supersedes(olderVersion.getId())
                .build());

        assertEquals(olderVersion.getId(), newer.getSupersedesId());
    }

    @Test
    void findsTheCurrentJudgmentOfAPullRequestWhicheverVersionItWasMadeOn() {
        Judgment olderVersion =
                store.write(aDigestOf(pullRequest).inputHash("one").build());
        assertEquals(
                olderVersion.getId(),
                store.findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, "opf/openproject#1")
                        .orElseThrow()
                        .getId());
        JudgmentSubject newerVersion =
                JudgmentSubject.pullRequestRecord(JudgmentSubjectRows.insertPullRequestRecord(jdbc, 2));

        Judgment newer = store.write(aDigestOf(newerVersion)
                .inputHash("two")
                .supersedes(olderVersion.getId())
                .build());

        assertEquals(
                newer.getId(),
                store.findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, "opf/openproject#1")
                        .orElseThrow()
                        .getId());
        assertTrue(store.findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, "opf/openproject#2")
                .isEmpty());
    }
}
