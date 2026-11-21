package com.dsinnovators.ei.judgment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Reads the fields of a model answer and refuses one that breaks its schema, naming field and value. */
final class AnswerFields {

    private AnswerFields() {}

    static <E extends Enum<E> & PullRequestDigest.WireNamed> E enumOf(Class<E> type, JsonNode answer, String field) {
        JsonNode node = required(answer, field);
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> node.isString() && constant.wireName().equals(node.asString()))
                .findFirst()
                .orElseThrow(() -> new InvalidAnswerException("The field " + field + " must be one of "
                        + Arrays.stream(type.getEnumConstants())
                                .map(PullRequestDigest.WireNamed::wireName)
                                .toList() + ", was " + node));
    }

    static boolean flagOf(JsonNode answer, String field) {
        JsonNode node = required(answer, field);
        if (!node.isBoolean()) {
            throw new InvalidAnswerException("The field " + field + " must be true or false, was " + node);
        }
        return node.booleanValue();
    }

    static String textOf(JsonNode answer, String field) {
        JsonNode node = required(answer, field);
        if (!node.isString() || node.asString().isBlank()) {
            throw new InvalidAnswerException("The field " + field + " must be a text that is not blank, was " + node);
        }
        return node.asString();
    }

    /** A text that may be empty, such as a quote or a source. */
    static String textOrEmptyOf(JsonNode answer, String field) {
        JsonNode node = required(answer, field);
        if (!node.isString()) {
            throw new InvalidAnswerException("The field " + field + " must be a text, was " + node);
        }
        return node.asString();
    }

    static List<String> textsOf(JsonNode answer, String field) {
        JsonNode node = required(answer, field);
        if (!node.isArray()) {
            throw new InvalidAnswerException("The field " + field + " must be a list of texts, was " + node);
        }
        List<String> texts = new ArrayList<>();
        for (JsonNode item : node.values()) {
            if (!item.isString() || item.asString().isBlank()) {
                throw new InvalidAnswerException(
                        "The field " + field + " must hold texts that are not blank, had " + item);
            }
            texts.add(item.asString());
        }
        return List.copyOf(texts);
    }

    /** The field, which must be present. */
    static JsonNode required(JsonNode answer, String field) {
        if (!answer.has(field)) {
            throw new InvalidAnswerException("The field " + field + " is missing from the answer");
        }
        return answer.get(field);
    }

    static JsonNode objectOf(JsonNode answer, String field) {
        JsonNode node = required(answer, field);
        if (!node.isObject()) {
            throw new InvalidAnswerException("The field " + field + " must be an object, was " + node);
        }
        return node;
    }
}
