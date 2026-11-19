package com.dsinnovators.ei.source;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Escapes the character U+0000, which PostgreSQL's {@code jsonb} cannot store, as the six
 * characters {@code \u0000} in every key and text value of a payload (F02 design 5.4).
 */
final class NulCharacters {

    private static final String NUL = "\u0000";
    private static final String ESCAPED_NUL = "\\u0000";

    private NulCharacters() {}

    /** The payload with every NUL escaped, and whether there was any. */
    record Escaped(JsonNode payload, boolean anyEscaped) {}

    static Escaped escape(JsonNode payload) {
        if (!contains(payload)) {
            return new Escaped(payload, false);
        }
        return new Escaped(escaped(payload), true);
    }

    private static boolean contains(JsonNode node) {
        if (node.isString()) {
            return node.stringValue().contains(NUL);
        }
        if (node.isObject()) {
            return node.properties().stream()
                    .anyMatch(property -> property.getKey().contains(NUL) || contains(property.getValue()));
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                if (contains(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Value nodes other than text are kept as they are, so numbers keep their exact form.
    private static JsonNode escaped(JsonNode node) {
        if (node.isString()) {
            return JsonNodeFactory.instance.stringNode(escaped(node.stringValue()));
        }
        if (node.isObject()) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                object.set(escaped(property.getKey()), escaped(property.getValue()));
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            for (JsonNode element : node) {
                array.add(escaped(element));
            }
            return array;
        }
        return node;
    }

    private static String escaped(String text) {
        return text.replace(NUL, ESCAPED_NUL);
    }
}
