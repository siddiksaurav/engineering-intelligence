package com.dsinnovators.ei.source.github;

import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * The account that wrote a stored pull request or review comment.
 *
 * @param typename GitHub's {@code __typename} of the account, such as {@code User} or {@code Bot}
 * @param databaseId the account's numeric id, which stays when the login changes
 */
public record GitHubAuthor(String login, String typename, long databaseId) {

    /** The author of the payload; empty when GitHub names none, as for a deleted account. */
    static Optional<GitHubAuthor> of(JsonNode payload) {
        JsonNode author = payload.path("author");
        if (!author.isObject()) {
            return Optional.empty();
        }
        return Optional.of(new GitHubAuthor(
                author.path("login").asString(),
                author.path("__typename").asString(),
                author.path("databaseId").asLong()));
    }
}
