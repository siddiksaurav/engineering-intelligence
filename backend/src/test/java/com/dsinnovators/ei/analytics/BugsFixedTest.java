package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BugsFixedTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private BugsFixed bugsFixed;

    @Test
    void countsTheCurrentBugFixItemsOfThePersonInTheQuarter() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        for (int number = 1; number <= 7; number++) {
            rows.pullRequest(number, 1, 11, "ada", "2026-07-0" + number + "T10:00:00Z");
        }
        long first = rows.item("bug_fix", ada, "2026-07-01", false, null, 1);
        long second = rows.item("bug_fix", ada, "2026-07-02", false, null, 2, 3);
        long superseded = rows.item("bug_fix", ada, "2026-07-03", false, null, 4);
        rows.item("bug_fix", ada, "2026-07-03", true, superseded, 4);
        rows.item("bug_fix", grace, "2026-07-04", false, null, 5);
        rows.item("bug_fix", ada, "2026-06-30", false, null, 6);
        rows.item("feature_work", ada, "2026-07-05", false, null, 7);

        Count result = bugsFixed.of(ada, QUARTER);

        assertEquals(2, result.count());
        assertEquals(List.of(first, second), result.evidenceItemIds());
        assertEquals(List.of(1, 2, 3), result.pullRequestNumbers());
    }

    @Test
    void isNoneForAPersonWithoutBugFixItems() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");

        assertEquals(Count.none(), bugsFixed.of(ada, QUARTER));
    }
}
