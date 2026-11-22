package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * Everything needed to work a dimension score out again by hand (design, "The trail"). Stored as JSONB.
 *
 * @param evidenceTypesVersion the quarter the evidence type mapping is valid from, as text such as 2026-Q2
 * @param gatesVersion the quarter the gate mapping is valid from; null when the dimension has no gate
 * @param settings the scoring settings used
 * @param evidenceTypes the tiered evidence types with their items and presence
 * @param levelThreeRule null when the rule does not apply
 * @param workingTier null when no tier has presence
 * @param scoreBeforeGates the score from working tier and level, before the gates
 * @param gates one result per gate of the dimension
 * @param countedNotTiered items of types that are counted but have no tier (Bug fix)
 * @param notes the notes of the score, for example "held: G1"
 * @param finalScore the score after the gates
 */
public record DimensionTrail(
        String dimensionKey,
        Level level,
        String evidenceTypesVersion,
        String gatesVersion,
        ScoringSettings settings,
        List<EvidenceTypeTrail> evidenceTypes,
        LevelThreeRule levelThreeRule,
        Tier workingTier,
        int scoreBeforeGates,
        List<GateResult> gates,
        List<TrailItem> countedNotTiered,
        List<String> notes,
        int finalScore) {}
