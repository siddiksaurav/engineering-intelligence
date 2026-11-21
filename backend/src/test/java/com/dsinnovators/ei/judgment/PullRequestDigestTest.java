package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class PullRequestDigestTest {

    private static ObjectNode validAnswer() {
        return (ObjectNode) CanonicalJson.parse("""
                {
                  "workType": "fix",
                  "modulesTouched": ["meeting", "core"],
                  "schemaOrDataMigration": false,
                  "newIntegration": false,
                  "natureOfChange": "Stops the meeting list from crashing on an empty date.",
                  "testsIncluded": true,
                  "testKind": "regression",
                  "workTypeTier": {"tier": "not_applicable", "reason": ""},
                  "securityKind": "not_applicable",
                  "infraKind": "not_applicable",
                  "infraScope": "not_applicable",
                  "dependencyChanges": false,
                  "documentationScope": "not_applicable",
                  "reason": "The title and the spec name a crash that is now handled.",
                  "facts": [
                    {"fact": "Adds a regression spec for the empty date", "filePath": "spec/meetings_spec.rb"},
                    {"fact": "The title says the list crashed", "filePath": ""}
                  ]
                }
                """);
    }

    private static PullRequestDigest read(ObjectNode answer) {
        return PullRequestDigest.fromAnswer(answer, false);
    }

    @Test
    void refusesAWorkTypeOutsideTheThirteen() {
        ObjectNode answer = validAnswer().put("workType", "bugfix");

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("workType"));
        assertTrue(refused.getMessage().contains("bugfix"));
    }

    @Test
    void readsAValidAnswer() {
        PullRequestDigest digest = PullRequestDigest.fromAnswer(validAnswer(), true);

        assertEquals(PullRequestDigest.WorkType.FIX, digest.workType());
        assertEquals(List.of("meeting", "core"), digest.modulesTouched());
        assertEquals(PullRequestDigest.TestKind.REGRESSION, digest.testKind());
        assertEquals(Optional.empty(), digest.workTypeTier().tier());
        assertEquals(
                new PullRequestDigest.Fact("Adds a regression spec for the empty date", "spec/meetings_spec.rb"),
                digest.facts().getFirst());
        assertEquals(
                new PullRequestDigest.Fact("The title says the list crashed", ""),
                digest.facts().get(1));
        assertTrue(digest.testsIncluded());
        assertFalse(digest.schemaOrDataMigration());
        assertTrue(digest.judgedOnPartialCode());
    }

    @ParameterizedTest
    @EnumSource(PullRequestDigest.WorkType.class)
    void acceptsEachOfTheThirteenWorkTypes(PullRequestDigest.WorkType workType) {
        ObjectNode answer = validAnswer().put("workType", workType.wireName());
        answer.set("workTypeTier", CanonicalJson.parse("{\"tier\":\"T2\",\"reason\":\"A small speed-up\"}"));

        assertEquals(workType, read(answer).workType());
    }

    @Test
    void thereAreThirteenWorkTypes() {
        assertEquals(13, PullRequestDigest.WorkType.values().length);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "workType",
                "modulesTouched",
                "schemaOrDataMigration",
                "newIntegration",
                "natureOfChange",
                "testsIncluded",
                "testKind",
                "workTypeTier",
                "securityKind",
                "infraKind",
                "infraScope",
                "dependencyChanges",
                "documentationScope",
                "reason",
                "facts"
            })
    void refusesAMissingField(String field) {
        ObjectNode answer = validAnswer();
        answer.remove(field);

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains(field), refused.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"testKind", "securityKind", "infraKind", "infraScope", "documentationScope"})
    void refusesAValueOutsideAnEnum(String field) {
        ObjectNode answer = validAnswer().put(field, "sometimes");

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains(field));
        assertTrue(refused.getMessage().contains("sometimes"));
    }

    @Test
    void refusesAYesNoFieldThatIsNotABoolean() {
        ObjectNode answer = validAnswer().put("testsIncluded", "yes");

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @Test
    void refusesABlankReason() {
        assertThrows(InvalidAnswerException.class, () -> read(validAnswer().put("reason", " ")));
    }

    @Test
    void refusesAnAnswerWithoutFacts() {
        ObjectNode answer = validAnswer();
        answer.putArray("facts");

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @Test
    void refusesAFactWithoutText() {
        ObjectNode answer = validAnswer();
        answer.set("facts", CanonicalJson.parse("[{\"fact\":\" \",\"filePath\":\"a.rb\"}]"));

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @Test
    void refusesAnAnswerThatIsNotAnObject() {
        assertThrows(
                InvalidAnswerException.class, () -> PullRequestDigest.fromAnswer(CanonicalJson.parse("[]"), false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refactor", "performance", "security", "infra"})
    void refusesATierlessAnswerForAWorkTypeThatNeedsATier(String workType) {
        ObjectNode answer = validAnswer().put("workType", workType);

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("workTypeTier"));
    }

    @Test
    void readsTheTierOfARefactor() {
        ObjectNode answer = validAnswer().put("workType", "refactor");
        answer.set("workTypeTier", CanonicalJson.parse("{\"tier\":\"T3\",\"reason\":\"Planned debt item\"}"));

        PullRequestDigest.WorkTypeTier tier = read(answer).workTypeTier();

        assertEquals(Optional.of("T3"), tier.tier());
        assertEquals("Planned debt item", tier.reason());
    }

    @Test
    void refusesATierOutsideT1ToT3() {
        ObjectNode answer = validAnswer().put("workType", "infra");
        answer.set("workTypeTier", CanonicalJson.parse("{\"tier\":\"T4\",\"reason\":\"Too much\"}"));

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @Test
    void theStoredValueHoldsTheDigestAndTheSystemsPartialCodeFlag() {
        JsonNode value = PullRequestDigest.fromAnswer(validAnswer(), true).toValue();

        assertEquals("fix", value.path("workType").asString());
        assertEquals("regression", value.path("testKind").asString());
        assertEquals("not_applicable", value.path("workTypeTier").path("tier").asString());
        assertTrue(value.path("judgedOnPartialCode").asBoolean());
        assertEquals(
                "spec/meetings_spec.rb",
                value.path("facts").get(0).path("filePath").asString());
        assertEquals(16, value.size());
    }
}
