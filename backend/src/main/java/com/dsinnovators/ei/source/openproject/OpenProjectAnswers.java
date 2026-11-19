package com.dsinnovators.ei.source.openproject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
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

    /**
     * @param workPackage the number of the work package the activities belong to, for the error
     * @throws OpenProjectApiException when the answer has no list of elements or an activity has no
     *     {@code createdAt} that reads as a timestamp
     */
    static void requireActivities(JsonNode answer, long workPackage) {
        String call = "the activities of work package " + workPackage;
        for (JsonNode activity : elementsOf(answer, call)) {
            String createdAt = activity.path("createdAt").asString("");
            try {
                Instant.parse(createdAt);
            } catch (DateTimeParseException invalidTime) {
                throw new OpenProjectApiException(
                        "OpenProject's answer to " + call + " has activity "
                                + activity.path("id").asString("unknown") + " without a valid \"createdAt\": \""
                                + createdAt + "\"",
                        invalidTime);
            }
        }
    }

    private static OpenProjectApiException malformed(String call, String field) {
        return new OpenProjectApiException("OpenProject's answer to " + call + " has an element without " + field);
    }
}
