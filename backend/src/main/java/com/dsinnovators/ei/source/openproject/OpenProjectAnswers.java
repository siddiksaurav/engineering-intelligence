package com.dsinnovators.ei.source.openproject;

import tools.jackson.databind.JsonNode;

/** Checks the shape of what OpenProject answers, at the boundary, so that no malformed answer is stored. */
final class OpenProjectAnswers {

    private static final String WORK_PACKAGE_TYPE = "WorkPackage";

    private OpenProjectAnswers() {}

    /**
     * @param call what was asked, in words that name the call in an error
     * @return the list at {@code _embedded.elements}
     * @throws OpenProjectApiException when that field is not a list
     */
    static JsonNode elementsOf(JsonNode answer, String call) {
        JsonNode elements = answer.path("_embedded").path("elements");
        if (!elements.isArray()) {
            throw new OpenProjectApiException(
                    "OpenProject's answer to " + call + " has no list in \"_embedded.elements\"");
        }
        return elements;
    }

    /**
     * @param call what was asked, in words that name the call in an error
     * @throws OpenProjectApiException when the element has no numeric {@code id}, no {@code _links}
     *     object or a {@code _type} other than {@code WorkPackage}
     */
    static void requireWorkPackage(JsonNode element, String call) {
        if (!element.path("id").isIntegralNumber()) {
            throw malformed(call, "\"id\"");
        }
        if (!element.path("_links").isObject()) {
            throw malformed(call, "\"_links\"");
        }
        if (!WORK_PACKAGE_TYPE.equals(element.path("_type").asString())) {
            throw malformed(call, "\"_type\" of the kind " + WORK_PACKAGE_TYPE);
        }
    }

    private static OpenProjectApiException malformed(String call, String field) {
        return new OpenProjectApiException("OpenProject's answer to " + call + " has an element without " + field);
    }
}
