package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RepositoryHygieneTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private RepositoryHygiene hygiene;

    @Test
    void sharesTheHumanPullRequestsOfTheQuarterLinkedToAWorkItem() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        rows.link(1, "resolved");
        rows.pullRequest(2, 1, 12, "grace", "2026-07-03T10:00:00Z");
        rows.link(2, "not_visible");
        rows.pullRequest(3, 1, 13, "linus", "2026-07-04T10:00:00Z");
        rows.botPullRequest(4, 14, "dependabot", "2026-07-05T10:00:00Z");
        rows.pullRequest(5, 1, 11, "ada", "2026-06-30T23:59:59Z");
        rows.link(5, "resolved");
        rows.pullRequest(3, 2, 13, "linus", "2026-07-04T10:00:00Z");

        RepositoryHygiene.Result result = hygiene.of(QUARTER);

        assertEquals(3, result.total());
        assertEquals(List.of(1), result.linked().pullRequestNumbers());
        assertEquals(List.of(2, 3), result.notLinked().pullRequestNumbers());
        assertEquals(1.0 / 3, result.share());
    }

    @Test
    void hasNoShareWhenNoHumanPullRequestWasMergedInTheQuarter() {
        RepositoryHygiene.Result result = hygiene.of(QUARTER);

        assertEquals(0, result.total());
        assertNull(result.share());
    }
}
