package com.dsinnovators.ei.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.scoring.DimensionMapping;
import com.dsinnovators.ei.scoring.DimensionMappings;
import com.dsinnovators.ei.scoring.EvidenceTypeTrail;
import com.dsinnovators.ei.scoring.Level;
import com.dsinnovators.ei.scoring.ScoredItem;
import com.dsinnovators.ei.scoring.ScoredItems;
import com.dsinnovators.ei.scoring.ScoringSettingsReader;
import com.dsinnovators.ei.scoring.Tier;
import com.dsinnovators.ei.scoring.TierPresence;
import com.dsinnovators.ei.scoring.WorkingTierCalculator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkLevelTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");
    private static final String CODING = "coding_and_code_quality";

    @Autowired
    private WorkLevel workLevel;

    @Autowired
    private ScoringSettingsReader settingsReader;

    @Autowired
    private DimensionMappings mappings;

    @Autowired
    private ScoredItems scoredItems;

    private AnalyticsRows rows;
    private long ada;
    private long grace;

    @BeforeEach
    void createPersons() {
        rows = new AnalyticsRows(jdbc);
        ada = rows.person(11, "ada");
        grace = rows.person(12, "grace");
    }

    private void featureWork(long personId, String tier, String complexity, int count) {
        for (int index = 0; index < count; index++) {
            long item = rows.item("feature_work", personId, "2026-07-02", false, null);
            rows.complexity(item, complexity, tier, null);
        }
    }

    @Test
    void twoLowAndFiveMediumGiveEightyThreePercentAtT2() {
        featureWork(ada, "T1", "low", 2);
        featureWork(ada, "T2", "medium", 5);

        WorkLevel.Result result = workLevel.of(ada, QUARTER);

        assertEquals(12, result.person().weightedTotal());
        assertEquals(10, result.person().weightedAtT2OrAbove());
        assertEquals(10.0 / 12, result.person().shareAtT2OrAbove());
        assertEquals(0, result.person().weightedAtT3());
        assertEquals(0.0, result.person().shareAtT3());
        assertEquals(1.0 / 3, result.line());
    }

    @Test
    void theRestOfTheRepositoryLeavesThePersonOutAndVeryHighCountsAsT3() {
        featureWork(ada, "T1", "low", 1);
        featureWork(grace, "T3", "very_high", 1);
        featureWork(grace, "T1", "low", 2);

        WorkLevel.Result result = workLevel.of(ada, QUARTER);

        assertEquals(1, result.person().weightedTotal());
        assertEquals(6, result.restOfRepository().weightedTotal());
        assertEquals(4, result.restOfRepository().weightedAtT2OrAbove());
        assertEquals(4, result.restOfRepository().weightedAtT3());
        assertEquals(4.0 / 6, result.restOfRepository().shareAtT3());
    }

    @Test
    void itemsWithoutAJudgmentAndOutsideTheQuarterAreLeftOut() {
        featureWork(ada, "T2", "medium", 1);
        rows.item("feature_work", ada, "2026-07-03", false, null);
        long june = rows.item("feature_work", ada, "2026-06-30", false, null);
        rows.complexity(june, "high", "T3", null);

        WorkLevel.Result result = workLevel.of(ada, QUARTER);

        assertEquals(2, result.person().weightedTotal());
        assertEquals(2, result.person().weightedAtT2OrAbove());
        assertEquals(0, result.person().weightedAtT3());
    }

    @Test
    void aPersonWithoutJudgedFeatureWorkHasNoShare() {
        rows.item("feature_work", ada, "2026-07-03", false, null);

        WorkLevel.Result result = workLevel.of(ada, QUARTER);

        assertEquals(0, result.person().weightedTotal());
        assertNull(result.person().shareAtT2OrAbove());
        assertNull(result.person().shareAtT3());
        assertNull(result.restOfRepository().shareAtT2OrAbove());
    }

    @Test
    void aChangedWeightSettingChangesTheShare() {
        featureWork(ada, "T1", "low", 2);
        featureWork(ada, "T2", "medium", 5);
        jdbc.update("delete from setting where key = 'scoring.feature_work_weights'");
        jdbc.update("""
                insert into setting (key, valid_from_quarter, value, description)
                values ('scoring.feature_work_weights', '2026-Q2', '{"T1":3,"T2":1,"T3":4}'::jsonb, 'test')
                """);

        WorkLevel.Result result = workLevel.of(ada, QUARTER);

        assertEquals(11, result.person().weightedTotal());
        assertEquals(5.0 / 11, result.person().shareAtT2OrAbove());
    }

    @Test
    void theShareEqualsTheFeatureWorkPresenceOnTheCodingTrail() {
        featureWork(ada, "T1", "low", 3);
        featureWork(ada, "T2", "medium", 2);
        featureWork(ada, "T3", "high", 1);
        rows.item("feature_work", ada, "2026-07-09", false, null);

        WorkLevel.Result result = workLevel.of(ada, QUARTER);

        List<ScoredItem> items = scoredItems.of(QUARTER).get(ada);
        DimensionMapping coding = mappings.inForce(QUARTER).stream()
                .filter(mapping -> mapping.key().equals(CODING))
                .findFirst()
                .orElseThrow();
        EvidenceTypeTrail trail = new WorkingTierCalculator(settingsReader.in(QUARTER))
                .calculate(coding, Level.L2, items).evidenceTypes().stream()
                        .filter(candidate -> candidate.evidenceType().equals("feature_work"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(presenceAt(trail, Tier.T2).total(), result.person().weightedTotal());
        assertEquals(presenceAt(trail, Tier.T2).atOrAbove(), result.person().weightedAtT2OrAbove());
        assertEquals(presenceAt(trail, Tier.T3).atOrAbove(), result.person().weightedAtT3());
    }

    private static TierPresence presenceAt(EvidenceTypeTrail trail, Tier tier) {
        return trail.presence().stream()
                .filter(presence -> presence.tier() == tier)
                .findFirst()
                .orElseThrow();
    }
}
