package com.dsinnovators.ei.common;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The canonical form of a JSON value and its content hash.
 *
 * <p>The canonical form has object keys sorted by code point at every depth, no white space, and
 * arrays in their given order. Numbers keep their digits, trailing zeros included, so {@code 1}
 * and {@code 1.0} are different content; they are written in plain decimal form, so {@code 1E+2}
 * becomes {@code 100}. This is the form PostgreSQL's {@code jsonb} gives back, so a payload read
 * from the database has the same hash as when it was stored. The same content gives the same hash
 * whatever key order the source used.
 *
 * <p>Build the tree with {@link #parse(String)}. A tree read by another mapper may hold numbers as
 * binary floating point, which spells them differently and gives another hash.
 *
 * <p>This rule must not change once records are stored: a new rule would make every stored record
 * look changed.
 */
public final class CanonicalJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private static final Comparator<String> BY_CODE_POINT = (left, right) ->
            Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());

    private CanonicalJson() {}

    /** Reads JSON text and keeps every number exact. */
    public static JsonNode parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON text is required");
        }
        return MAPPER.readTree(json);
    }

    /** The canonical form of the value as text. */
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
            writeObject(node, out);
        } else if (node.isArray()) {
            writeArray(node, out);
        } else if (node.isString()) {
            out.append(quote(node.stringValue()));
        } else if (node.isNumber()) {
            out.append(number(node.numberValue()));
        } else if (node.isBoolean()) {
            out.append(node.booleanValue());
        } else if (node.isNull()) {
            out.append("null");
        } else {
            throw new IllegalArgumentException("Not a JSON value: " + node.getNodeType());
        }
    }

    private static void writeObject(JsonNode object, StringBuilder out) {
        List<Map.Entry<String, JsonNode>> properties = object.properties().stream()
                .sorted(Map.Entry.comparingByKey(BY_CODE_POINT))
                .toList();
        String separator = "";
        out.append('{');
        for (Map.Entry<String, JsonNode> property : properties) {
            out.append(separator).append(quote(property.getKey())).append(':');
            write(property.getValue(), out);
            separator = ",";
        }
        out.append('}');
    }

    private static void writeArray(JsonNode array, StringBuilder out) {
        String separator = "";
        out.append('[');
        for (JsonNode element : array) {
            out.append(separator);
            write(element, out);
            separator = ",";
        }
        out.append(']');
    }

    /** Plain decimal form, never an exponent: the form PostgreSQL's jsonb gives back. */
    private static String number(Number value) {
        return value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    }

    private static String quote(String text) {
        return MAPPER.writeValueAsString(text);
    }
}
