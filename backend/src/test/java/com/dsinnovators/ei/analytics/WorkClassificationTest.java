package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkClassificationTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private WorkClassification classification;

    @Test
    void countsEachMergedPullRequestOfThePersonByItsDigestWorkType() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        rows.person(12, "grace");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        long supersededDigest = rows.digest(1, "fix", null);
        rows.digest(1, "feature", supersededDigest);
        rows.pullRequest(2, 1, 11, "ada", "2026-07-03T10:00:00Z");
        rows.pullRequest(2, 2, 11, "ada", "2026-07-03T10:00:00Z");
        rows.digest(2, "feature", null);
        rows.pullRequest(3, 1, 11, "ada", "2026-07-04T10:00:00Z");
        rows.digest(3, "fix", null);
        rows.pullRequest(4, 1, 11, "ada", "2026-07-05T10:00:00Z");
        rows.pullRequest(5, 1, 12, "grace", "2026-07-06T10:00:00Z");
        rows.digest(5, "docs", null);

        Distribution result = classification.of(ada, QUARTER);

        assertEquals(4, result.total());
        assertEquals(
                List.of("feature", "fix", "not_judged_yet"),
                result.slices().stream().map(Slice::name).toList());
        assertEquals(List.of(1, 2), result.slices().get(0).count().pullRequestNumbers());
        assertEquals(2, result.slices().get(0).count().count());
        assertEquals(List.of(3), result.slices().get(1).count().pullRequestNumbers());
        assertEquals(List.of(4), result.slices().get(2).count().pullRequestNumbers());
        assertEquals(0.5, result.slices().get(0).share());
        assertEquals(1.0, result.slices().stream().mapToDouble(Slice::share).sum());
    }

    @Test
    void aPersonWithNoPullRequestHasNoSlices() {
        long ada = new AnalyticsRows(jdbc).person(11, "ada");

        Distribution result = classification.of(ada, QUARTER);

        assertEquals(0, result.total());
        assertEquals(List.of(), result.slices());
    }
}
