package com.dsinnovators.ei.source.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The GraphQL queries, kept as files under {@code github/} on the classpath. A fragment lives once
 * under {@code github/fragments/}, named after it in kebab case ({@code ReviewComment} is {@code
 * review-comment.graphql}), and is added to every query that uses it, so the first page and every
 * further page of a part ask for the same fields. A query decides which fields are stored, so a
 * change to one is a decision (F02 design 6.2).
 */
final class GraphQlDocuments {

    // "...Name" spreads a fragment; "... on Type" is an inline fragment and needs no file.
    private static final Pattern FRAGMENT_SPREAD = Pattern.compile("\\.\\.\\.([A-Z]\\w*)");
    private static final Pattern WORD_START = Pattern.compile("(?<!^)([A-Z])");

    private GraphQlDocuments() {}

    /** The text of {@code github/<name>.graphql} with every fragment it uses, directly or not. */
    static String read(String name) {
        String query = file("github/" + name + ".graphql");
        StringBuilder document = new StringBuilder(query);
        for (String fragment : fragmentsUsedBy(query)) {
            document.append('\n').append(fragmentText(fragment));
        }
        return document.toString();
    }

    private static Set<String> fragmentsUsedBy(String query) {
        Set<String> fragments = new LinkedHashSet<>();
        Deque<String> toRead = new ArrayDeque<>();
        toRead.push(query);
        while (!toRead.isEmpty()) {
            Matcher spread = FRAGMENT_SPREAD.matcher(toRead.pop());
            while (spread.find()) {
                if (fragments.add(spread.group(1))) {
                    toRead.push(fragmentText(spread.group(1)));
                }
            }
        }
        return fragments;
    }

    private static String fragmentText(String fragment) {
        String kebab = WORD_START.matcher(fragment).replaceAll("-$1").toLowerCase(Locale.ROOT);
        return file("github/fragments/" + kebab + ".graphql");
    }

    private static String file(String path) {
        try (InputStream in = GraphQlDocuments.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No GraphQL query on the classpath at " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the GraphQL query " + path, e);
        }
    }
}
