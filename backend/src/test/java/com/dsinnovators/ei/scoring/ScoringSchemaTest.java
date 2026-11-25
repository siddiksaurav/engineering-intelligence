package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScoringSchemaTest extends DatabaseTest {

    @Test
    void seedMapsTheTwoDimensionsToTheirEvidenceTypesAndGates() {
        List<Map<String, Object>> mapping = jdbc.queryForList("""
                select dimension.key as dimension, evidence_type, valid_from_quarter, tiered, from_level
                from dimension_evidence_type
                         join dimension on dimension.id = dimension_evidence_type.dimension_id
                order by dimension.key, evidence_type
                """);
        assertEquals(
                List.of(
                        row("coding_and_code_quality", "feature_work", true, "L1"),
                        row("coding_and_code_quality", "review_comment_written", true, "L2"),
                        row("engineering_standards_and_system_health", "bug_fix", false, "L1"),
                        row("engineering_standards_and_system_health", "infra_release", true, "L1"),
                        row("engineering_standards_and_system_health", "refactor_performance", true, "L1"),
                        row("engineering_standards_and_system_health", "security", true, "L1"),
                        row("engineering_standards_and_system_health", "test_change", true, "L1")),
                mapping);

        List<Map<String, Object>> gates = jdbc.queryForList("""
                select dimension.key as dimension, gate, valid_from_quarter, applied
                from dimension_gate
                         join dimension on dimension.id = dimension_gate.dimension_id
                order by dimension.key, gate
                """);
        assertEquals(List.of(gate("G1", true), gate("G2", true), gate("G4", false)), gates);
    }

    @Test
    void theEightScoringSettingsAreSeeded() {
        List<String> keys =
                jdbc.queryForList("select key from setting where key like 'scoring.%' order by key", String.class);
        assertEquals(
                List.of(
                        "scoring.feature_work_weights",
                        "scoring.frequent_evidence_types",
                        "scoring.gate_limits",
                        "scoring.gate_minimum_developers",
                        "scoring.gate_minimum_pull_requests_per_developer",
                        "scoring.gate_scored",
                        "scoring.presence_threshold",
                        "scoring.t3_items_needed_at_l3"),
                keys);
    }

    private static Map<String, Object> row(String dimension, String evidenceType, boolean tiered, String fromLevel) {
        return Map.of(
                "dimension", dimension,
                "evidence_type", evidenceType,
                "valid_from_quarter", "2026-Q2",
                "tiered", tiered,
                "from_level", fromLevel);
    }

    private static Map<String, Object> gate(String gate, boolean applied) {
        return Map.of(
                "dimension",
                "engineering_standards_and_system_health",
                "gate",
                gate,
                "valid_from_quarter",
                "2026-Q2",
                "applied",
                applied);
    }
}
