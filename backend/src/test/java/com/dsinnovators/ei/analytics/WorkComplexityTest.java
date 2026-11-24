package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkComplexityTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");
    private static final List<String> BARS = List.of("low", "medium", "high", "very_high", "not_judged_yet");

    @Autowired
    private WorkComplexity complexity;

    @Test
    void countsThePersonsFeatureWorkByComplexityNextToTheRepository() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        rows.pullRequest(1, 1, 11, "ada", "2026-07-02T10:00:00Z");
        rows.pullRequest(2, 1, 11, "ada", "2026-07-03T10:00:00Z");
        long low = rows.item("feature_work", ada, "2026-07-02", false, null, 1);
        rows.complexity(low, "low", "T1", null);
        long high = rows.item("feature_work", ada, "2026-07-03", false, null, 1, 2);
        long supersededJudgment = rows.complexity(high, "medium", "T2", null);
        rows.complexity(high, "high", "T3", supersededJudgment);
        long veryHigh = rows.item("feature_work", ada, "2026-07-04", false, null);
        rows.complexity(veryHigh, "very_high", "T3", null);
        long unjudged = rows.item("feature_work", ada, "2026-07-05", false, null);
        long othersMedium = rows.item("feature_work", grace, "2026-07-06", false, null);
        rows.complexity(othersMedium, "medium", "T2", null);
        long supersededItem = rows.item("feature_work", ada, "2026-07-07", false, null);
        rows.complexity(supersededItem, "low", "T1", null);
        rows.item("feature_work", ada, "2026-07-07", true, supersededItem);
        long beforeTheQuarter = rows.item("feature_work", ada, "2026-06-30", false, null);
        rows.complexity(beforeTheQuarter, "low", "T1", null);
        rows.item("bug_fix", ada, "2026-07-08", false, null);

        WorkComplexity.Result result = complexity.of(ada, QUARTER);

        assertEquals(4, result.person().total());
        assertEquals(BARS, names(result.person()));
        assertEquals(List.of(1L, 0L, 1L, 1L, 1L), counts(result.person()));
        assertEquals(List.of(low), result.person().slices().get(0).count().evidenceItemIds());
        assertEquals(List.of(1), result.person().slices().get(0).count().pullRequestNumbers());
        assertEquals(List.of(1, 2), result.person().slices().get(2).count().pullRequestNumbers());
        assertEquals(List.of(unjudged), result.person().slices().get(4).count().evidenceItemIds());
        assertEquals(0.25, result.person().slices().get(2).share());
        assertEquals(1, result.restOfRepository().total());
        assertEquals(List.of(0L, 1L, 0L, 0L, 0L), counts(result.restOfRepository()));
        assertEquals(
                List.of(othersMedium),
                result.restOfRepository().slices().get(1).count().evidenceItemIds());
    }

    @Test
    void restOfRepositoryLeavesThePersonOut() {
        AnalyticsRows rows = new AnalyticsRows(jdbc);
        long ada = rows.person(11, "ada");
        long grace = rows.person(12, "grace");
        for (int day = 2; day <= 3; day++) {
            long medium = rows.item("feature_work", ada, "2026-07-0" + day, false, null);
            rows.complexity(medium, "medium", "T2", null);
        }
        long high = rows.item("feature_work", grace, "2026-07-04", false, null);
        rows.complexity(high, "high", "T3", null);

        WorkComplexity.Result result = complexity.of(ada, QUARTER);

        assertEquals(List.of(0L, 2L, 0L, 0L, 0L), counts(result.person()));
        assertEquals(List.of(0L, 0L, 1L, 0L, 0L), counts(result.restOfRepository()));
    }

    @Test
    void showsAllFourBarsAndTheNotJudgedYetBarWithoutAnyShareWhenThereIsNoFeatureWork() {
        long ada = new AnalyticsRows(jdbc).person(11, "ada");

        WorkComplexity.Result result = complexity.of(ada, QUARTER);

        assertEquals(BARS, names(result.person()));
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L), counts(result.restOfRepository()));
        assertNull(result.person().slices().get(0).share());
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
