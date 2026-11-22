package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.analytics.AnalyticsRows;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ScoredItemsTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ScoredItems scoredItems;

    private AnalyticsRows rows;
    private long ada;
    private long grace;

    @BeforeEach
    void storePersons() {
        rows = new AnalyticsRows(jdbc);
        ada = rows.person(11, "ada");
        grace = rows.person(12, "grace");
        for (int number = 1; number <= 3; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
        }
    }

    @Test
    void readsCurrentItemsOfTheQuarterWithTheTierOfTheirKind() {
        long feature = rows.item("feature_work", ada, "2026-07-01", false, null, 1);
        long firstComplexity = rows.complexity(feature, "medium", "T2", null);
        rows.complexity(feature, "very_high", "T3", firstComplexity);
        long refactor = rows.item("refactor_performance", ada, "2026-07-02", false, null, 2);
        rows.itemTier(refactor, "T1");
        long testChange = rows.testChange(ada, 3, "2026-07-03");
        rows.itemTier(testChange, "T2");
        rows.item("bug_fix", ada, "2026-07-03", false, null, 3);
        rows.item("security", ada, "2026-07-03", false, null, 3);
        long substantive = rows.reviewComment(ada, 1, null, "2026-07-04");
        rows.commentJudgment(substantive, "bug_found", true, "T3", null);
        long trivial = rows.reviewComment(ada, 2, null, "2026-07-04");
        rows.commentJudgment(trivial, "nit", false, null, null);
        rows.reviewComment(ada, 3, null, "2026-07-04");

        List<ScoredItem> items = scoredItems.of(QUARTER).get(ada);

        assertEquals(8, items.size());
        ScoredItem featureItem = itemOfType(items, EvidenceType.FEATURE_WORK);
        assertEquals(Tier.T3, featureItem.tier());
        assertEquals("very_high", featureItem.complexity());
        assertEquals(List.of(1), featureItem.pullRequestNumbers());
        assertEquals("https://example.org", featureItem.sourceUrl());
        assertEquals(
                refactor, itemOfType(items, EvidenceType.REFACTOR_PERFORMANCE).evidenceItemId());
        assertEquals(
                Tier.T1, itemOfType(items, EvidenceType.REFACTOR_PERFORMANCE).tier());
        ScoredItem testItem = itemOfType(items, EvidenceType.TEST_CHANGE);
        assertEquals(Tier.T2, testItem.tier());
        assertNull(testItem.complexity());
        assertEquals(
                JudgmentState.NOT_JUDGED_YET,
                itemOfType(items, EvidenceType.BUG_FIX).judgmentState());
        assertEquals(
                JudgmentState.NOT_JUDGED_YET,
                itemOfType(items, EvidenceType.SECURITY).judgmentState());
        assertNull(itemOfType(items, EvidenceType.SECURITY).tier());
        List<ScoredItem> comments = items.stream()
                .filter(item -> item.evidenceType() == EvidenceType.REVIEW_COMMENT_WRITTEN)
                .toList();
        assertEquals(
                List.of(JudgmentState.JUDGED, JudgmentState.NOT_SUBSTANTIVE, JudgmentState.NOT_JUDGED_YET),
                comments.stream().map(ScoredItem::judgmentState).toList());
        assertEquals(Tier.T3, comments.getFirst().tier());
        assertEquals(List.of(1), comments.getFirst().pullRequestNumbers());
    }

    @Test
    void leavesOutWithdrawnSupersededAndOutOfQuarterItems() {
        rows.item("feature_work", ada, "2026-07-01", false, null, 1);
        long superseded = rows.item("bug_fix", ada, "2026-07-02", false, null, 2);
        rows.item("bug_fix", ada, "2026-07-02", true, superseded, 2);
        rows.item("security", ada, "2026-06-30", false, null, 3);
        rows.item("security", ada, "2026-10-01", false, null, 3);

        List<ScoredItem> items = scoredItems.of(QUARTER).get(ada);

        assertEquals(
                List.of(EvidenceType.FEATURE_WORK),
                items.stream().map(ScoredItem::evidenceType).toList());
    }

    @Test
    void scoresPersonsWithAnItemOrAHumanPullRequestAndNoBot() {
        rows.item("security", grace, "2026-07-05", false, null, 1);
        long linus = rows.person(13, "linus");
        rows.pullRequest(4, 1, 13, "linus", "2026-07-06T10:00:00Z");
        rows.person(14, "outsider");
        rows.pullRequest(5, 1, 14, "outsider", "2026-05-06T10:00:00Z");
        rows.botPullRequest(6, 15, "dependabot[bot]", "2026-07-07T10:00:00Z");

        Map<Long, List<ScoredItem>> items = scoredItems.of(QUARTER);

        assertEquals(Set.of(ada, grace, linus), items.keySet());
        assertTrue(items.get(linus).isEmpty());
        assertEquals(1, items.get(grace).size());
    }

    private static ScoredItem itemOfType(List<ScoredItem> items, EvidenceType type) {
        return items.stream()
                .filter(item -> item.evidenceType() == type)
                .findFirst()
                .orElseThrow();
    }
}
