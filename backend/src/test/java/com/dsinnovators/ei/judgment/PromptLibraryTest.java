package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class PromptLibraryTest {

    private static final String VERSION = "pull-request-digest/v1";
    private static final String FEATURE_WORK_VERSION = "feature-work-complexity/v1";

    /**
     * The SHA-256 of each released prompt file (decision 129). A released prompt is never edited: a
     * change is a new version file, and its judgments get a new input hash. Add the new file here.
     */
    private static final Map<String, String> RELEASED_PROMPT_HASHES = Map.of(
            "judgment/prompts/pull-request-digest-v1.md",
            "f2da6360862df4f97e641ac7a8f3acf236ee8c8de0281881b55e520041d9875f",
            "judgment/prompts/feature-work-complexity-v1.md",
            "2e72717d21a26afef37c41df019abe8e5ddcb33eca2a21d1d96e75cef1879451");

    /** The same for the schema of each released prompt. */
    private static final Map<String, String> RELEASED_SCHEMA_HASHES = Map.of(
            "judgment/schemas/pull-request-digest-v1.json",
            "27fedee68a274de8dc7b5ea2a9cd7563d31a47af1fa4b6944bee286f78dd0010",
            "judgment/schemas/feature-work-complexity-v1.json",
            "625d61212e88fcfd792e94fce9fa776f7432cee962c6ccf0e033821da8d10626");

    private final PromptLibrary library = new PromptLibrary();

    private static String sha256Of(String resource) throws IOException, NoSuchAlgorithmException {
        try (InputStream stream = PromptLibraryTest.class.getClassLoader().getResourceAsStream(resource)) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }

    @Test
    void loadsThePromptOfAVersion() {
        String instructions = library.instructions(VERSION);

        assertTrue(instructions.contains("# Work type"));
        assertTrue(instructions.contains("Size is not complexity"));
    }

    @Test
    void thePromptNamesEachOfTheThirteenWorkTypes() {
        String instructions = library.instructions(VERSION);

        for (PullRequestDigest.WorkType workType : PullRequestDigest.WorkType.values()) {
            assertTrue(instructions.contains("`" + workType.wireName() + "`"), workType.wireName());
        }
    }

    @Test
    void theSchemaIsStrict() {
        JsonNode schema = library.schema(VERSION);

        assertStrict(schema);
        assertEquals("object", schema.path("type").asString());
        assertTrue(schema.path("properties").has("workType"));
    }

    private static void assertStrict(JsonNode schema) {
        if (schema.path("type").asString().equals("object")) {
            assertEquals(false, schema.path("additionalProperties").asBoolean(true));
            assertEquals(
                    schema.path("properties").propertyNames().stream().sorted().toList(),
                    schema.path("required")
                            .valueStream()
                            .map(JsonNode::asString)
                            .sorted()
                            .toList());
            schema.path("properties").forEachEntry((name, property) -> assertStrict(property));
        }
        if (schema.has("items")) {
            assertStrict(schema.path("items"));
        }
    }

    @Test
    void theSchemaHoldsExactlyTheFieldsTheModelAnswers() {
        JsonNode schema = library.schema(VERSION);

        assertEquals(15, schema.path("properties").size());
        assertEquals(false, schema.path("properties").has("judgedOnPartialCode"));
        assertEquals(false, schema.path("properties").has("testTier"));
    }

    @Test
    void theFeatureWorkPromptNamesTheThirteenWorkTypesAndTheFourComplexities() {
        String instructions = library.instructions(FEATURE_WORK_VERSION);

        for (PullRequestDigest.WorkType workType : PullRequestDigest.WorkType.values()) {
            assertTrue(instructions.contains("`" + workType.wireName() + "`"), workType.wireName());
        }
        for (FeatureWorkAnswer.Complexity complexity : FeatureWorkAnswer.Complexity.values()) {
            assertTrue(instructions.contains("`" + complexity.wireName() + "`"), complexity.wireName());
        }
    }

    @Test
    void theFeatureWorkPromptStatesTheModuleRuleAndTheTwoLimits() {
        String instructions = library.instructions(FEATURE_WORK_VERSION);

        assertTrue(instructions.contains("never decides the complexity alone"));
        assertTrue(instructions.contains("a rename across five modules"));
        assertTrue(instructions.contains("Size is not complexity"));
        assertTrue(instructions.contains("Never call work hard because it is large"));
        assertTrue(instructions.contains("L1 is about the person's own task"));
        assertTrue(instructions.contains("Quote only the input"));
    }

    @Test
    void theFeatureWorkSchemaIsStrictAndItsEnumsEqualTheAnswerTypes() {
        JsonNode schema = library.schema(FEATURE_WORK_VERSION);
        JsonNode properties = schema.path("properties");

        assertStrict(schema);
        assertEquals(6, properties.size());
        assertEquals(wireNames(PullRequestDigest.WorkType.values()), enumValues(properties.path("workType")));
        assertEquals(wireNames(FeatureWorkAnswer.Complexity.values()), enumValues(properties.path("complexity")));
        assertEquals(
                wireNames(FeatureWorkAnswer.DataChange.values()),
                enumValues(properties.path("rubric").path("properties").path("dataChange")));
        assertEquals(
                wireNames(FeatureWorkAnswer.Integration.values()),
                enumValues(properties.path("rubric").path("properties").path("integration")));
    }

    private static List<String> wireNames(PullRequestDigest.WireNamed[] values) {
        return Arrays.stream(values).map(PullRequestDigest.WireNamed::wireName).toList();
    }

    private static List<String> enumValues(JsonNode property) {
        return property.path("enum").valueStream().map(JsonNode::asString).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pull-request-digest/v9", "unknown/v1", "../secrets/v1", "pull-request-digest", ""})
    void refusesAVersionThatIsNotReleased(String version) {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> library.instructions(version));

        assertTrue(refused.getMessage().contains(version));
    }

    @Test
    void releasedPromptFilesAreUnchanged() throws Exception {
        for (Map.Entry<String, String> released : RELEASED_PROMPT_HASHES.entrySet()) {
            assertEquals(released.getValue(), sha256Of(released.getKey()), released.getKey());
        }
    }

    @Test
    void releasedSchemaFilesAreUnchanged() throws Exception {
        for (Map.Entry<String, String> released : RELEASED_SCHEMA_HASHES.entrySet()) {
            assertEquals(released.getValue(), sha256Of(released.getKey()), released.getKey());
        }
    }
}
