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
                  "problems": [
                    {
                      "name": "Store a recurring series",
                      "depth": "answer_exists",
                      "depthReason": "A known pattern covers it, with no earlier example.",
                      "quote": "adds a migration",
                      "source": "PR-24204"
                    },
                    {
                      "name": "Meeting form",
                      "depth": "routine",
                      "depthReason": "A standard Rails form.",
                      "quote": "",
                      "source": "WI-81224"
                    }
                  ],
                  "problemsAffectEachOther": false,
                  "problemsAffectEachOtherReason": "",
                  "mistakesStayHidden": false,
                  "mistakesStayHiddenReason": "",
                  "reachesAcrossProjects": false,
                  "thinEvidence": false,
                  "complexity": "medium",
                  "reason": "One problem has a known answer and the project had no example.",
                  "confidence": 0.8
                }
                """);
    }

    private static ObjectNode problem(ObjectNode answer, int index) {
        return (ObjectNode) ((ArrayNode) answer.get("problems")).get(index);
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
        assertEquals(2, answer.problems().size());
        assertEquals(
                new FeatureWorkAnswer.Problem(
                        "Store a recurring series",
                        FeatureWorkAnswer.Depth.ANSWER_EXISTS,
                        "A known pattern covers it, with no earlier example.",
                        "adds a migration",
                        "PR-24204"),
                answer.problems().get(0));
        assertEquals(FeatureWorkAnswer.Depth.ROUTINE, answer.problems().get(1).depth());
        assertFalse(answer.problemsAffectEachOther());
        assertFalse(answer.mistakesStayHidden());
        assertFalse(answer.reachesAcrossProjects());
        assertFalse(answer.thinEvidence());
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
    void refusesAnAnswerWithoutAProblem() {
        ObjectNode none = validAnswer();
        none.set("problems", CanonicalJson.parse("[]"));
        ObjectNode missing = validAnswer();
        missing.remove("problems");

        assertThrows(InvalidAnswerException.class, () -> read(none));
        assertThrows(InvalidAnswerException.class, () -> read(missing));
    }

    @ParameterizedTest
    @ValueSource(strings = {"easy", "Routine", "answer exists", ""})
    void refusesADepthOutsideTheThree(String depth) {
        ObjectNode answer = validAnswer();
        problem(answer, 0).put("depth", depth);

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("depth"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "depthReason"})
    void refusesAProblemWithABlankNameOrDepthReason(String field) {
        ObjectNode answer = validAnswer();
        problem(answer, 0).put(field, " ");

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @ParameterizedTest
    @ValueSource(strings = {"quote", "source"})
    void refusesAProblemWithoutItsQuoteOrSourceField(String field) {
        ObjectNode answer = validAnswer();
        problem(answer, 0).remove(field);

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "problemsAffectEachOther",
                "problemsAffectEachOtherReason",
                "mistakesStayHidden",
                "mistakesStayHiddenReason",
                "reachesAcrossProjects",
                "thinEvidence"
            })
    void refusesAnAnswerWithAFlagOrItsReasonMissing(String field) {
        ObjectNode answer = validAnswer();
        answer.remove(field);

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains(field));
    }

    @Test
    void refusesABlankReason() {
        assertThrows(InvalidAnswerException.class, () -> read(validAnswer().put("reason", " ")));
    }

    @Test
    void refusesAnAnswerThatIsNotAnObject() {
        assertThrows(InvalidAnswerException.class, () -> FeatureWorkAnswer.fromAnswer(CanonicalJson.parse("[]")));
    }

    @Test
    void keepsTheComplexityOfTheModelWhenTheTableDiffers() {
        ObjectNode answer = validAnswer().put("complexity", "high");

        JsonNode value = read(answer).toValue(false, List.of());

        assertEquals("high", value.path("complexity").asString());
        assertEquals("medium", value.path("complexityByTable").asString());
    }

    @Test
    void storesTheAnswerAndWhatTheSystemSetAsTheValueOfAJudgment() {
        ObjectNode answer = validAnswer()
                .put("problemsAffectEachOther", true)
                .put("problemsAffectEachOtherReason", "The series and the form share one model.")
                .put("thinEvidence", true);

        JsonNode value = read(answer).toValue(true, List.of("opf/openproject#9"));

        assertEquals("feature", value.path("workType").asString());
        assertEquals("medium", value.path("complexity").asString());
        assertEquals(2, value.path("problems").size());
        JsonNode first = value.path("problems").get(0);
        assertEquals("Store a recurring series", first.path("name").asString());
        assertEquals("answer_exists", first.path("depth").asString());
        assertEquals("adds a migration", first.path("quote").asString());
        assertEquals("PR-24204", first.path("source").asString());
        assertEquals("not_checked", first.path("earlierExample").asString());
        assertEquals(
                "not_checked",
                value.path("problems").get(1).path("earlierExample").asString());
        assertTrue(value.path("problemsAffectEachOther").asBoolean());
        assertEquals(
                "The series and the form share one model.",
                value.path("problemsAffectEachOtherReason").asString());
        assertFalse(value.path("mistakesStayHidden").asBoolean());
        assertFalse(value.path("reachesAcrossProjects").asBoolean());
        assertTrue(value.path("thinEvidence").asBoolean());
        assertEquals("medium", value.path("complexityByTable").asString());
        assertEquals(
                "One problem has a known answer and the project had no example.",
                value.path("reason").asString());
        assertTrue(value.path("confidence").isMissingNode());
        assertTrue(value.path("rubric").isMissingNode());
        assertTrue(value.path("judgedOnPartialInput").asBoolean());
        assertEquals(
                "opf/openproject#9", value.path("leftOutPullRequests").get(0).asString());
    }
}
