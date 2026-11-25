package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import com.dsinnovators.ei.setting.SettingService;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Turns the {@code scoring.*} settings in force for a quarter into {@link ScoringSettings}. A missing
 * setting or a value of the wrong shape fails with the key in the message (decision 24).
 */
@Component
public class ScoringSettingsReader {

    private static final String PRESENCE_THRESHOLD = "scoring.presence_threshold";
    private static final String FEATURE_WORK_WEIGHTS = "scoring.feature_work_weights";
    private static final String FREQUENT_EVIDENCE_TYPES = "scoring.frequent_evidence_types";
    private static final String T3_ITEMS_NEEDED_AT_L3 = "scoring.t3_items_needed_at_l3";
    private static final String GATE_LIMITS = "scoring.gate_limits";
    private static final String GATE_SCORED = "scoring.gate_scored";
    private static final String GATE_MINIMUM_DEVELOPERS = "scoring.gate_minimum_developers";
    private static final String GATE_MINIMUM_PULL_REQUESTS = "scoring.gate_minimum_pull_requests_per_developer";

    private final SettingService settings;

    ScoringSettingsReader(SettingService settings) {
        this.settings = settings;
    }

    public ScoringSettings in(Quarter quarter) {
        Map<Gate, GateLimits> gateLimits = gateLimits(quarter);
        return new ScoringSettings(
                presenceThreshold(quarter),
                featureWorkWeights(quarter),
                frequentEvidenceTypes(quarter),
                t3ItemsNeededAtLevel3(quarter),
                gateLimits,
                gateScored(quarter, gateLimits.keySet()),
                settings.getInt(GATE_MINIMUM_DEVELOPERS, quarter),
                settings.getInt(GATE_MINIMUM_PULL_REQUESTS, quarter));
    }

    private PresenceThreshold presenceThreshold(Quarter quarter) {
        JsonNode value = settings.getJson(PRESENCE_THRESHOLD, quarter);
        try {
            return new PresenceThreshold(
                    whole(PRESENCE_THRESHOLD, value, "numerator"), whole(PRESENCE_THRESHOLD, value, "denominator"));
        } catch (IllegalArgumentException e) {
            throw invalid(PRESENCE_THRESHOLD, value, e);
        }
    }

    private Map<Tier, Integer> featureWorkWeights(Quarter quarter) {
        JsonNode value = settings.getJson(FEATURE_WORK_WEIGHTS, quarter);
        Map<Tier, Integer> weights = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            weights.put(tier, whole(FEATURE_WORK_WEIGHTS, value, tier.name()));
        }
        return weights;
    }

    private Set<EvidenceType> frequentEvidenceTypes(Quarter quarter) {
        List<String> names = settings.getTextList(FREQUENT_EVIDENCE_TYPES, quarter);
        try {
            return names.stream()
                    .map(name -> EvidenceType.valueOf(name.toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw invalid(FREQUENT_EVIDENCE_TYPES, settings.getJson(FREQUENT_EVIDENCE_TYPES, quarter), e);
        }
    }

    private Map<String, Integer> t3ItemsNeededAtLevel3(Quarter quarter) {
        JsonNode value = settings.getJson(T3_ITEMS_NEEDED_AT_L3, quarter);
        Map<String, Integer> itemsNeeded = new HashMap<>();
        value.properties()
                .forEach(entry -> itemsNeeded.put(entry.getKey(), whole(T3_ITEMS_NEEDED_AT_L3, value, entry.getKey())));
        return itemsNeeded;
    }

    private Map<Gate, GateLimits> gateLimits(Quarter quarter) {
        JsonNode value = settings.getJson(GATE_LIMITS, quarter);
        Map<Gate, GateLimits> limits = new EnumMap<>(Gate.class);
        for (Map.Entry<String, JsonNode> entry : value.properties()) {
            try {
                limits.put(
                        Gate.valueOf(entry.getKey()),
                        new GateLimits(
                                whole(GATE_LIMITS, entry.getValue(), "passWithinPoints"),
                                whole(GATE_LIMITS, entry.getValue(), "farMissBeyondPoints")));
            } catch (IllegalArgumentException e) {
                throw invalid(GATE_LIMITS, value, e);
            }
        }
        return limits;
    }

    private Map<Gate, Boolean> gateScored(Quarter quarter, Set<Gate> gatesWithLimits) {
        JsonNode value = settings.getJson(GATE_SCORED, quarter);
        Map<Gate, Boolean> scored = new EnumMap<>(Gate.class);
        for (Gate gate : gatesWithLimits) {
            JsonNode switchOfGate = value.get(gate.name());
            if (switchOfGate == null || !switchOfGate.isBoolean()) {
                throw new IllegalStateException("Setting '" + GATE_SCORED + "' needs true or false for gate " + gate
                        + " in quarter " + quarter + ", got " + value);
            }
            scored.put(gate, switchOfGate.booleanValue());
        }
        return scored;
    }

    private static int whole(String key, JsonNode object, String field) {
        JsonNode number = object.get(field);
        if (number == null || !number.isIntegralNumber() || !number.canConvertToInt()) {
            throw new IllegalStateException(
                    "Setting '" + key + "' needs a whole number in '" + field + "', got " + object);
        }
        return number.intValue();
    }

    private static IllegalStateException invalid(String key, JsonNode value, IllegalArgumentException cause) {
        return new IllegalStateException(
                "Setting '" + key + "' has an invalid value " + value + ": " + cause.getMessage(), cause);
    }
}
