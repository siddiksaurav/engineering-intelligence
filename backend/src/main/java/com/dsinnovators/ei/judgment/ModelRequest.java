package com.dsinnovators.ei.judgment;

import tools.jackson.databind.JsonNode;

/**
 * One request to the model.
 *
 * @param promptVersion such as {@code pull-request-digest/v1}; stored with the judgment
 * @param instructions the prompt, sent as the system message
 * @param input what is judged, sent as the user message
 * @param schemaName the name of the answer's JSON schema
 * @param jsonSchema the strict JSON schema the answer must follow
 */
public record ModelRequest(
        String promptVersion, String instructions, String input, String schemaName, JsonNode jsonSchema) {

    public ModelRequest {
        if (isBlank(promptVersion) || isBlank(instructions) || isBlank(input) || isBlank(schemaName)) {
            throw new IllegalArgumentException(
                    "A model request needs a prompt version, instructions, an input and a schema name");
        }
        if (jsonSchema == null || !jsonSchema.isObject()) {
            throw new IllegalArgumentException("A model request needs a JSON schema object");
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
