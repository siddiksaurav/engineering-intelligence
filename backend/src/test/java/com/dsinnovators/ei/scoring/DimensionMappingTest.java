package com.dsinnovators.ei.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DimensionMappingTest extends DatabaseTest {

    private static final Quarter Q2_2026 = Quarter.parse("2026-Q2");
    private static final Quarter Q3_2026 = Quarter.parse("2026-Q3");
    private static final Quarter Q4_2026 = Quarter.parse("2026-Q4");
    private static final String STANDARDS = "engineering_standards_and_system_health";

    @Autowired
    private DimensionMappings mappings;

    private DimensionMapping standardsIn(Quarter quarter) {
        return mappings.inForce(quarter).stream()
                .filter(mapping -> mapping.key().equals(STANDARDS))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void readsTheSeededMappingAndGatesOfBothDimensions() {
        List<DimensionMapping> inForce = mappings.inForce(Q3_2026);

        assertEquals(
                List.of("coding_and_code_quality", STANDARDS),
                inForce.stream().map(DimensionMapping::key).toList());
        DimensionMapping coding = inForce.getFirst();
        assertEquals("Coding and code quality", coding.name());
        assertEquals(Q2_2026, coding.evidenceTypesVersion());
        assertEquals(
                List.of(
                        new MappedEvidenceType(EvidenceType.FEATURE_WORK, true, Level.L1),
                        new MappedEvidenceType(EvidenceType.REVIEW_COMMENT_WRITTEN, true, Level.L2)),
                coding.evidenceTypes());
        assertEquals(Optional.empty(), coding.gatesVersion());
        assertEquals(List.of(), coding.gates());

        DimensionMapping standards = inForce.getLast();
        assertEquals(Optional.of(Q2_2026), standards.gatesVersion());
        assertEquals(
                List.of(new MappedGate(Gate.G1, true), new MappedGate(Gate.G2, true), new MappedGate(Gate.G4, false)),
                standards.gates());
        assertFalse(standards.evidenceTypes().contains(new MappedEvidenceType(EvidenceType.BUG_FIX, true, Level.L1)));
        assertTrue(standards.evidenceTypes().contains(new MappedEvidenceType(EvidenceType.BUG_FIX, false, Level.L1)));
    }

    @Test
    void aMappingValidFromALaterQuarterDoesNotChangeAnEarlierOne() {
        jdbc.update("""
                insert into dimension_evidence_type (dimension_id, evidence_type, valid_from_quarter, tiered, from_level)
                select id, 'test_change', '2026-Q4', true, 'L2' from dimension where key = ?
                """, STANDARDS);
        jdbc.update("""
                insert into dimension_gate (dimension_id, gate, valid_from_quarter, applied)
                select id, 'G1', '2026-Q4', false from dimension where key = ?
                """, STANDARDS);

        DimensionMapping inQ3 = standardsIn(Q3_2026);
        assertEquals(Q2_2026, inQ3.evidenceTypesVersion());
        assertEquals(5, inQ3.evidenceTypes().size());
        assertEquals(Optional.of(Q2_2026), inQ3.gatesVersion());
        assertEquals(3, inQ3.gates().size());

        DimensionMapping inQ4 = standardsIn(Q4_2026);
        assertEquals(Q4_2026, inQ4.evidenceTypesVersion());
        assertEquals(List.of(new MappedEvidenceType(EvidenceType.TEST_CHANGE, true, Level.L2)), inQ4.evidenceTypes());
        assertEquals(Optional.of(Q4_2026), inQ4.gatesVersion());
        assertEquals(List.of(new MappedGate(Gate.G1, false)), inQ4.gates());
    }

    @Test
    void aQuarterBeforeTheFirstMappingFailsNamingTheDimension() {
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> mappings.inForce(Quarter.parse("2026-Q1")));

        assertTrue(e.getMessage().contains("coding_and_code_quality"));
        assertTrue(e.getMessage().contains("2026-Q1"));
    }

    @Test
    void aTypeIsReadFromItsLowestLevel() {
        MappedEvidenceType reviewComments = new MappedEvidenceType(EvidenceType.REVIEW_COMMENT_WRITTEN, true, Level.L2);

        assertFalse(reviewComments.isReadAt(Level.L1));
        assertTrue(reviewComments.isReadAt(Level.L2));
        assertTrue(reviewComments.isReadAt(Level.L3));
    }
}
