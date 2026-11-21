package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

class PromptLibraryTest {

    private static final String VERSION = "pull-request-digest/v1";

    /**
     * The SHA-256 of each released prompt file (decision 129). A released prompt is never edited: a
     * change is a new version file, and its judgments get a new input hash. Add the new file here.
     */
    private static final Map<String, String> RELEASED_PROMPT_HASHES = Map.of(
            "judgment/prompts/pull-request-digest-v1.md",
            "f2da6360862df4f97e641ac7a8f3acf236ee8c8de0281881b55e520041d9875f");

    /** The same for the schema of each released prompt. */
    private static final Map<String, String> RELEASED_SCHEMA_HASHES = Map.of(
            "judgment/schemas/pull-request-digest-v1.json",
            "27fedee68a274de8dc7b5ea2a9cd7563d31a47af1fa4b6944bee286f78dd0010");

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
