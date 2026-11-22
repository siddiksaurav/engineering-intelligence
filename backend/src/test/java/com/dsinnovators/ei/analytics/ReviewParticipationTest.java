package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewParticipationTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ReviewParticipation participation;

    @Test
    void countsPullRequestsReviewedAuthorsAndSubstantiveCommentsPerPullRequest() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        rows.pullRequest(1, 1, 12, "grace", "2026-07-02T10:00:00Z");
        rows.pullRequest(2, 1, 12, "grace", "2026-07-03T10:00:00Z");
        rows.botPullRequest(3, 99, "dependabot[bot]", "2026-07-04T10:00:00Z");
        rows.pullRequest(4, 1, 11, "ada", "2026-07-05T10:00:00Z");
        rows.pullRequest(5, 1, 12, "grace", "2026-06-30T10:00:00Z");
        rows.review(1, 11, "ada", "APPROVED");
        long smallCatch = rows.reviewComment(ada, 2, grace, "2026-07-03");
        rows.commentJudgment(smallCatch, "small_catch", true, "T1", null);
        long design = rows.reviewComment(ada, 2, grace, "2026-07-03");
        long supersededJudgment = rows.commentJudgment(design, "style", false, null, null);
        rows.commentJudgment(design, "design", true, "T3", supersededJudgment);
        long style = rows.reviewComment(ada, 2, grace, "2026-07-03");
        rows.commentJudgment(style, "style", false, null, null);
        long onBotPullRequest = rows.reviewComment(ada, 3, null, "2026-07-04");
        rows.review(4, 11, "ada", "COMMENTED");
        rows.review(4, 12, "grace", "APPROVED");
        long othersComment = rows.reviewComment(grace, 4, ada, "2026-07-05");
        rows.commentJudgment(othersComment, "correctness", true, "T2", null);
        long beforeTheQuarter = rows.reviewComment(ada, 5, grace, "2026-06-30");
        rows.commentJudgment(beforeTheQuarter, "correctness", true, "T2", null);
        rows.review(5, 11, "ada", "APPROVED");

        ReviewParticipation.Result result = participation.of(ada, QUARTER);

        assertEquals(3, result.pullRequestsReviewed().count());
        assertEquals(List.of(1, 2, 3), result.pullRequestsReviewed().pullRequestNumbers());
        assertEquals(1, result.authorsReviewed().count());
        assertEquals(List.of(1, 2), result.authorsReviewed().pullRequestNumbers());
        assertEquals(2, result.comments().substantive().count());
        assertEquals(
                List.of(smallCatch, design), result.comments().substantive().evidenceItemIds());
        assertEquals(2.0 / 3, result.substantivePerPullRequestReviewed());
        assertEquals(List.of("design", "small_catch"), names(result.comments().byCommentType()));
        assertEquals(List.of("T1", "T3"), names(result.comments().byTier()));
        assertEquals(0.5, result.comments().byTier().slices().get(0).share());
        assertEquals(List.of(onBotPullRequest), result.comments().notJudgedYet().evidenceItemIds());
    }

    @Test
    void countsNothingAndGivesNoShareForAPersonWhoReviewedNothing() {
        long ada = new AnalyticsRows(jdbc).person(11, "ada");

        ReviewParticipation.Result result = participation.of(ada, QUARTER);

        assertEquals(0, result.pullRequestsReviewed().count());
        assertEquals(0, result.authorsReviewed().count());
        assertNull(result.substantivePerPullRequestReviewed());
        assertEquals(0, result.comments().notJudgedYet().count());
    }

    private static List<String> names(Distribution distribution) {
        return distribution.slices().stream().map(Slice::name).toList();
    }
}
