package com.dsinnovators.ei.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The canonical form of a JSON value and its content hash.
 *
 * <p>The canonical form has object keys sorted by code point at every depth, no white space, and
 * arrays in their given order. Numbers stay as the source spelled them, so {@code 1} and
 * {@code 1.0} are different content. The same content gives the same hash whatever key order the
 * source used.
 *
 * <p>This rule must not change once records are stored: a new rule would make every stored record
 * look changed.
 */
public final class CanonicalJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private static final Comparator<String> BY_CODE_POINT = (left, right) -> {
        int[] a = left.codePoints().toArray();
        int[] b = right.codePoints().toArray();
        return Arrays.compare(a, b);
    };

    private CanonicalJson() {}

    /** Reads JSON text and keeps every number exact. */
    public static JsonNode parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON text is required");
        }
        return MAPPER.readTree(json);
    }

    public static String canonicalize(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("A JSON value is required");
        }
        StringBuilder out = new StringBuilder();
        write(node, out);
        return out.toString();
    }

    /** SHA-256 of the canonical form as UTF-8, in 64 lower-case hex characters. */
    public static String sha256Hex(JsonNode node) {
        byte[] canonical = canonicalize(node).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static void write(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> property : node.properties().stream()
                    .sorted(Map.Entry.comparingByKey(BY_CODE_POINT))
                    .toList()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(quote(property.getKey())).append(':');
                write(property.getValue(), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            boolean first = true;
            for (JsonNode element : node) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(element, out);
            }
            out.append(']');
        } else if (node.isString()) {
            out.append(quote(node.stringValue()));
        } else if (node.isNumber()) {
            out.append(node.numberValue().toString());
        } else if (node.isBoolean()) {
            out.append(node.booleanValue());
        } else if (node.isNull()) {
            out.append("null");
        } else {
            throw new IllegalArgumentException("Not a JSON value: " + node.getNodeType());
        }
    }

    private static String quote(String text) {
        return MAPPER.writeValueAsString(text);
    }
}
