package com.dsinnovators.ei.judgment;

import tools.jackson.databind.JsonNode;

/**
 * A model request that waits in the model exchange for its answer.
 *
 * @param requestHash identifies the request; the answer is given under it
 * @param promptVersion such as {@code pull-request-digest/v1}
 * @param instructions the prompt, as a provider would get it in the system message
 * @param input what is judged, as a provider would get it in the user message
 * @param schemaName the name of the answer's JSON schema
 * @param jsonSchema the strict JSON schema the answer must follow
 * @param refusalReason why the last answer was refused; null when none was
 */
public record ExchangeRequest(
        String requestHash,
        String promptVersion,
        String instructions,
        String input,
        String schemaName,
        JsonNode jsonSchema,
        String refusalReason) {}
