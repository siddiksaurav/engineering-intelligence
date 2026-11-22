package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.setting.SettingNotFoundException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ScoringSettingsTest extends DatabaseTest {

    private static final Quarter Q3_2026 = Quarter.parse("2026-Q3");

    @Autowired
    private ScoringSettingsReader reader;

    private void replace(String key, String json) {
        jdbc.update("delete from setting where key = ?", key);
        jdbc.update(
                "insert into setting (key, valid_from_quarter, value, description) values (?, '2026-Q2', ?::jsonb, 'test')",
                key,
                json);
    }

    @Test
    void readsTheSeededValuesFor2026Q3() {
        ScoringSettings settings = reader.in(Q3_2026);

        assertEquals(new PresenceThreshold(1, 3), settings.presenceThreshold());
        assertEquals(Map.of(Tier.T1, 1, Tier.T2, 2, Tier.T3, 4), settings.featureWorkWeights());
        assertEquals(
                Set.of(EvidenceType.FEATURE_WORK, EvidenceType.REVIEW_COMMENT_WRITTEN, EvidenceType.TEST_CHANGE),
                settings.frequentEvidenceTypes());
        assertEquals(Map.of("engineering_standards_and_system_health", 2), settings.t3ItemsNeededAtLevel3());
        assertEquals(Map.of(Gate.G1, new GateLimits(10, 25), Gate.G2, new GateLimits(10, 25)), settings.gateLimits());
        assertEquals(3, settings.gateMinimumDevelopers());
        assertEquals(1, settings.gateMinimumPullRequestsPerDeveloper());
    }

    @Test
    void aMissingSettingFailsNamingTheKey() {
        jdbc.update("delete from setting where key = 'scoring.gate_limits'");

        SettingNotFoundException e = assertThrows(SettingNotFoundException.class, () -> reader.in(Q3_2026));

        assertEquals("scoring.gate_limits", e.getKey());
    }

    @Test
    void aWeightMissingForATierFailsNamingTheKey() {
        replace("scoring.feature_work_weights", "{\"T1\": 1, \"T2\": 2}");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> reader.in(Q3_2026));

        assertTrue(e.getMessage().contains("scoring.feature_work_weights"));
        assertTrue(e.getMessage().contains("T3"));
    }

    @Test
    void aThresholdAboveOneFailsNamingTheKey() {
        replace("scoring.presence_threshold", "{\"numerator\": 4, \"denominator\": 3}");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> reader.in(Q3_2026));

        assertTrue(e.getMessage().contains("scoring.presence_threshold"));
    }

    @Test
    void anUnknownEvidenceTypeFailsNamingTheKey() {
        replace("scoring.frequent_evidence_types", "[\"feature_work\", \"poetry\"]");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> reader.in(Q3_2026));

        assertTrue(e.getMessage().contains("scoring.frequent_evidence_types"));
    }

    @Test
    void aNewValueForALaterQuarterChangesTheThresholdOfThatQuarterOnly() {
        jdbc.update("""
                insert into setting (key, valid_from_quarter, value, description)
                values ('scoring.presence_threshold', '2026-Q4', '{"numerator": 1, "denominator": 2}'::jsonb, 'test')
                """);

        assertEquals(new PresenceThreshold(1, 3), reader.in(Q3_2026).presenceThreshold());
        assertEquals(
                new PresenceThreshold(1, 2), reader.in(Quarter.parse("2026-Q4")).presenceThreshold());
    }
}
