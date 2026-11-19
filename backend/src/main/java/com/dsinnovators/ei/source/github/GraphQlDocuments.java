package com.dsinnovators.ei.source.github;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The GraphQL queries, kept as files under {@code github/} on the classpath. A query decides which
 * fields are stored, so a change to one is a decision (F02 design 6.2).
 */
final class GraphQlDocuments {

    private GraphQlDocuments() {}

    /** The text of {@code github/<name>.graphql}. */
    static String read(String name) {
        String path = "github/" + name + ".graphql";
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
