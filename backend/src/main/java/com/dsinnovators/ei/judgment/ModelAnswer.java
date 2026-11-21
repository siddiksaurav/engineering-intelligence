package com.dsinnovators.ei.judgment;

import tools.jackson.databind.JsonNode;

/**
 * The answer of the model.
 *
 * @param answer the JSON the model gave, not yet checked against the schema
 * @param model the model id the provider answered with
 * @param inputTokens tokens of the request, as the provider counted them
 * @param outputTokens tokens of the answer
 */
public record ModelAnswer(JsonNode answer, String model, long inputTokens, long outputTokens) {}
