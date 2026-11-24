package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewCommentBreakdownTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ReviewFindingsReceived findings;

    @Test
    void countsNotSubstantiveComments() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        long substantive = rows.reviewComment(grace, 1, ada, "2026-07-02");
        rows.commentJudgment(substantive, "correctness", true, "T2", null);
        long notSubstantive = rows.reviewComment(grace, 1, ada, "2026-07-02");
        rows.commentJudgment(notSubstantive, "style", false, null, null);
        long unjudged = rows.reviewComment(grace, 1, ada, "2026-07-02");

        ReviewCommentBreakdown comments = findings.of(ada, QUARTER).comments();

        assertEquals(new Coverage(2, 3), comments.coverage());
        assertEquals(List.of(notSubstantive), comments.notSubstantive().evidenceItemIds());
        assertEquals(1, comments.notSubstantive().count());
        assertEquals(List.of(substantive), comments.substantive().evidenceItemIds());
        assertEquals(List.of(unjudged), comments.notJudgedYet().evidenceItemIds());
        assertEquals(1, comments.byTier().total());
    }
}
