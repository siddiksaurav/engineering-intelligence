package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class FeatureWorkAnswerTest {

    private static ObjectNode validAnswer() {
        return (ObjectNode) CanonicalJson.parse("""
                {
                  "workType": "feature",
                  "complexity": "medium",
                  "rubric": {
                    "modulesTouched": ["meeting", "core"],
                    "dataChange": "schema_change",
                    "integration": "none",
                    "requirementsUnclear": false,
                    "knownPattern": true,
                    "acrossSystems": false,
                    "newArchitecture": false,
                    "clientCriticalWithoutEarlierExample": false
                  },
                  "facts": [
                    {"fact": "Adds a column to the meetings table", "quote": "adds a migration", "source": "PR-24204"},
                    {"fact": "The ticket asks for recurring meetings", "quote": "Recurring meetings", "source": "WI-81224"}
                  ],
                  "reason": "Two modules and one schema change make this Medium.",
                  "confidence": 0.8
                }
                """);
    }

    private static FeatureWorkAnswer read(ObjectNode answer) {
        return FeatureWorkAnswer.fromAnswer(answer);
    }

    @Test
    void refusesAConfidenceAboveOne() {
        ObjectNode answer = validAnswer().put("confidence", 1.2);

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("confidence"));
        assertTrue(refused.getMessage().contains("1.2"));
    }

    @Test
    void refusesAConfidenceBelowZeroOrNotANumber() {
        assertThrows(InvalidAnswerException.class, () -> read(validAnswer().put("confidence", -0.1)));
        assertThrows(InvalidAnswerException.class, () -> read(validAnswer().put("confidence", "high")));
    }

    @Test
    void readsAValidAnswer() {
        FeatureWorkAnswer answer = read(validAnswer());

        assertEquals(PullRequestDigest.WorkType.FEATURE, answer.workType());
        assertEquals(FeatureWorkAnswer.Complexity.MEDIUM, answer.complexity());
        assertEquals("medium", answer.complexity().wireName());
        assertEquals(List.of("meeting", "core"), answer.rubric().modulesTouched());
        assertEquals(FeatureWorkAnswer.DataChange.SCHEMA_CHANGE, answer.rubric().dataChange());
        assertEquals(FeatureWorkAnswer.Integration.NONE, answer.rubric().integration());
        assertTrue(answer.rubric().knownPattern());
        assertFalse(answer.rubric().requirementsUnclear());
        assertEquals(2, answer.facts().size());
        assertEquals(
                new FeatureWorkAnswer.Fact("Adds a column to the meetings table", "adds a migration", "PR-24204"),
                answer.facts().get(0));
        assertEquals(0.8, answer.confidence());
    }

    @Test
    void readsVeryHighComplexity() {
        assertEquals(
                FeatureWorkAnswer.Complexity.VERY_HIGH,
                read(validAnswer().put("complexity", "very_high")).complexity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"trivial", "Low", "very high", ""})
    void refusesAComplexityOutsideTheList(String complexity) {
        InvalidAnswerException refused = assertThrows(
                InvalidAnswerException.class, () -> read(validAnswer().put("complexity", complexity)));

        assertTrue(refused.getMessage().contains("complexity"));
    }

    @Test
    void refusesAWorkTypeOutsideTheThirteen() {
        assertThrows(InvalidAnswerException.class, () -> read(validAnswer().put("workType", "epic")));
    }

    @Test
    void refusesAnAnswerWithoutAFact() {
        ObjectNode none = validAnswer();
        none.set("facts", CanonicalJson.parse("[]"));
        ObjectNode missing = validAnswer();
        missing.remove("facts");

        assertThrows(InvalidAnswerException.class, () -> read(none));
        assertThrows(InvalidAnswerException.class, () -> read(missing));
    }

    @Test
    void refusesAFactWithoutText() {
        ObjectNode answer = validAnswer();
        ((ObjectNode) ((ArrayNode) answer.get("facts")).get(0)).put("fact", " ");

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @Test
    void refusesAFactWithoutItsQuoteOrSourceField() {
        ObjectNode noQuote = validAnswer();
        ((ObjectNode) ((ArrayNode) noQuote.get("facts")).get(0)).remove("quote");
        ObjectNode noSource = validAnswer();
        ((ObjectNode) ((ArrayNode) noSource.get("facts")).get(0)).remove("source");

        assertThrows(InvalidAnswerException.class, () -> read(noQuote));
        assertThrows(InvalidAnswerException.class, () -> read(noSource));
    }

    @Test
    void refusesABlankReason() {
        assertThrows(InvalidAnswerException.class, () -> read(validAnswer().put("reason", " ")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "modulesTouched",
                "dataChange",
                "integration",
                "requirementsUnclear",
                "knownPattern",
                "acrossSystems",
                "newArchitecture",
                "clientCriticalWithoutEarlierExample"
            })
    void refusesARubricWithAFieldMissing(String field) {
        ObjectNode answer = validAnswer();
        ((ObjectNode) answer.get("rubric")).remove(field);

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains(field));
    }

    @Test
    void refusesADataChangeOrIntegrationOutsideTheList() {
        ObjectNode dataChange = validAnswer();
        ((ObjectNode) dataChange.get("rubric")).put("dataChange", "migration");
        ObjectNode integration = validAnswer();
        ((ObjectNode) integration.get("rubric")).put("integration", "api");

        assertThrows(InvalidAnswerException.class, () -> read(dataChange));
        assertThrows(InvalidAnswerException.class, () -> read(integration));
    }

    @Test
    void refusesAnAnswerThatIsNotAnObject() {
        assertThrows(InvalidAnswerException.class, () -> FeatureWorkAnswer.fromAnswer(CanonicalJson.parse("[]")));
    }

    @Test
    void storesTheAnswerAndWhatTheSystemSetAsTheValueOfAJudgment() {
        JsonNode value = read(validAnswer()).toValue(true, List.of("opf/openproject#9"));

        assertEquals("feature", value.path("workType").asString());
        assertEquals("medium", value.path("complexity").asString());
        assertEquals("schema_change", value.path("rubric").path("dataChange").asString());
        assertEquals("none", value.path("rubric").path("integration").asString());
        assertEquals(
                "meeting", value.path("rubric").path("modulesTouched").get(0).asString());
        assertTrue(value.path("rubric").path("knownPattern").asBoolean());
        assertEquals(2, value.path("facts").size());
        assertEquals("PR-24204", value.path("facts").get(0).path("source").asString());
        assertEquals(
                "adds a migration", value.path("facts").get(0).path("quote").asString());
        assertEquals(
                "Two modules and one schema change make this Medium.",
                value.path("reason").asString());
        assertEquals(0.8, value.path("confidence").asDouble());
        assertTrue(value.path("judgedOnPartialInput").asBoolean());
        assertEquals(
                "opf/openproject#9", value.path("leftOutPullRequests").get(0).asString());
    }
}
