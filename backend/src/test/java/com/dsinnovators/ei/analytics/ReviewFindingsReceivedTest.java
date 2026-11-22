package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewFindingsReceivedTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ReviewFindingsReceived findings;

    @Test
    void countsSubstantiveCommentsReceivedPerPullRequestLeavingStyleOut() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        rows.pullRequest(2, 1, 11, "ada", "2026-07-03T10:00:00Z");
        rows.pullRequest(3, 1, 12, "grace", "2026-07-04T10:00:00Z");
        long correctness = rows.reviewComment(grace, 1, ada, "2026-07-02");
        rows.commentJudgment(correctness, "correctness", true, "T2", null);
        long style = rows.reviewComment(grace, 1, ada, "2026-07-02");
        rows.commentJudgment(style, "style", false, null, null);
        long onOthersPullRequest = rows.reviewComment(ada, 3, grace, "2026-07-04");
        rows.commentJudgment(onOthersPullRequest, "design", true, "T3", null);
        long outsideTheQuarter = rows.reviewComment(grace, 1, ada, "2026-10-01");
        rows.commentJudgment(outsideTheQuarter, "design", true, "T3", null);

        ReviewFindingsReceived.Result result = findings.of(ada, QUARTER);

        assertEquals(2, result.pullRequests().count());
        assertEquals(1, result.comments().substantive().count());
        assertEquals(List.of(correctness), result.comments().substantive().evidenceItemIds());
        assertEquals(List.of(1), result.comments().substantive().pullRequestNumbers());
        assertEquals(0.5, result.substantivePerPullRequest());
        assertEquals(List.of("correctness"), names(result.comments().byCommentType()));
        assertEquals(List.of("T2"), names(result.comments().byTier()));
        assertEquals(0, result.comments().notJudgedYet().count());
    }

    @Test
    void countsCommentsWithoutAJudgmentApartAndGivesNoShareWithoutPullRequests() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        long unjudged = rows.reviewComment(grace, 1, ada, "2026-07-02");

        ReviewFindingsReceived.Result result = findings.of(ada, QUARTER);

        assertEquals(List.of(unjudged), result.comments().notJudgedYet().evidenceItemIds());
        assertEquals(0, result.comments().substantive().count());
        assertNull(findings.of(grace, QUARTER).substantivePerPullRequest());
    }

    private static List<String> names(Distribution distribution) {
        return distribution.slices().stream().map(Slice::name).toList();
    }
}
