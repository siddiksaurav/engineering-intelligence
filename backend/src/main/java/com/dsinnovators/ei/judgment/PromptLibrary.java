package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Loads the released prompts and their answer schemas. A version such as {@code
 * pull-request-digest/v1} is the file {@code judgment/prompts/pull-request-digest-v1.md} and the
 * schema {@code judgment/schemas/pull-request-digest-v1.json}. A released file is never edited: a
 * change is a new version (decision 129).
 */
@Component
class PromptLibrary {

    private static final Pattern VERSION = Pattern.compile("[a-z]+(-[a-z]+)*/v[0-9]+");

    /** The prompt, sent to the model as its instructions. */
    String instructions(String promptVersion) {
        return read("judgment/prompts/" + fileStem(promptVersion) + ".md", promptVersion);
    }

    /** The strict JSON schema the answer to this prompt follows. */
    JsonNode schema(String promptVersion) {
        return CanonicalJson.parse(read("judgment/schemas/" + fileStem(promptVersion) + ".json", promptVersion));
    }

    private static String fileStem(String promptVersion) {
        if (!VERSION.matcher(promptVersion).matches()) {
            throw new IllegalArgumentException(
                    "A prompt version looks like pull-request-digest/v1, was '" + promptVersion + "'");
        }
        return promptVersion.replace('/', '-');
    }

    private static String read(String resource, String promptVersion) {
        try (InputStream stream = PromptLibrary.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("There is no released prompt version '" + promptVersion + "'");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("The prompt file " + resource + " could not be read", failure);
        }
    }
}
