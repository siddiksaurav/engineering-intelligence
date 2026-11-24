package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TestingContributionTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private TestingContribution testing;

    @Test
    void sharesFeatureAndFixPullRequestsWithATestChange() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        for (int number = 1; number <= 5; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
        }
        rows.digest(1, "feature", null);
        rows.itemTier(rows.testChange(ada, 1, "2026-07-01"), "T1");
        rows.digest(2, "enhancement", null);
        rows.digest(3, "fix", null);
        rows.itemTier(rows.testChange(ada, 3, "2026-07-03"), "T2");
        rows.digest(4, "docs", null);
        rows.testChange(ada, 4, "2026-07-04");
        rows.pullRequest(6, 1, 12, "grace", "2026-07-06T10:00:00Z");
        rows.digest(6, "feature", null);
        rows.testChange(grace, 6, "2026-07-06");
        rows.pullRequest(7, 1, 12, "grace", "2026-07-07T10:00:00Z");

        TestingContribution.Result result = testing.of(ada, QUARTER);

        TestingContribution.Share featureAndFix = result.featureAndFixPullRequests();
        assertEquals(3, featureAndFix.total());
        assertEquals(List.of(1, 3), featureAndFix.tested().pullRequestNumbers());
        assertEquals(List.of(2), featureAndFix.untested().pullRequestNumbers());
        assertEquals(2.0 / 3, featureAndFix.share());
        assertEquals(List.of("T1", "T2"), names(featureAndFix.testedByTier()));
        assertEquals(List.of(1L, 1L), counts(featureAndFix.testedByTier()));
        assertEquals(List.of(5), result.withoutDigest().pullRequestNumbers());
        assertEquals(new Coverage(4, 5), result.coverage());
        assertEquals(0, result.bugFixPullRequests().total());
        assertNull(result.bugFixPullRequests().share());
    }

    @Test
    void sharesThePullRequestsOfBugFixItemsWithATestChangeOfAnyKind() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        for (int number = 1; number <= 4; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
        }
        rows.item("bug_fix", ada, "2026-07-01", false, null, 1);
        rows.testChange(ada, 1, "2026-07-01");
        rows.item("bug_fix", ada, "2026-07-02", false, null, 2, 3);
        long superseded = rows.item("bug_fix", ada, "2026-07-03", false, null, 4);
        rows.item("bug_fix", ada, "2026-07-03", true, superseded, 4);
        rows.item("bug_fix", grace, "2026-07-04", false, null, 4);
        rows.pullRequest(5, 1, 11, "ada", "2026-06-30T10:00:00Z");
        rows.item("bug_fix", ada, "2026-06-30", false, null, 5);
        rows.pullRequest(6, 1, 11, "ada", "2026-10-01T10:00:00Z");
        rows.item("bug_fix", ada, "2026-10-01", false, null, 6);

        TestingContribution.Result result = testing.of(ada, QUARTER);

        TestingContribution.Share bugFix = result.bugFixPullRequests();
        assertEquals(3, bugFix.total());
        assertEquals(List.of(1), bugFix.tested().pullRequestNumbers());
        assertEquals(List.of(2, 3), bugFix.untested().pullRequestNumbers());
        assertEquals(1.0 / 3, bugFix.share());
        assertEquals(List.of("not_judged_yet"), names(bugFix.testedByTier()));
        assertEquals(0, result.featureAndFixPullRequests().total());
    }

    @Test
    void findsTheTestChangeThroughItsSourcePullRequestWhateverItsKey() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-01T10:00:00Z");
        rows.digest(1, "feature", null);
        long testChange = rows.item("test_change", ada, "2026-07-01", false, null, 1);

        TestingContribution.Share featureAndFix = testing.of(ada, QUARTER).featureAndFixPullRequests();

        assertEquals(List.of(testChange), featureAndFix.tested().evidenceItemIds());
    }

    private static List<String> names(Distribution distribution) {
        return distribution.slices().stream().map(Slice::name).toList();
    }

    private static List<Long> counts(Distribution distribution) {
        return distribution.slices().stream()
                .map(slice -> slice.count().count())
                .toList();
    }
}
