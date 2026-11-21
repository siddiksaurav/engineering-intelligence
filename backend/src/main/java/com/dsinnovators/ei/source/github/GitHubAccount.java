package com.dsinnovators.ei.source.github;

/**
 * A GitHub account as the stored records show it. {@code databaseId} is stable when the login is
 * renamed; {@code typename} is GitHub's {@code __typename}, for example {@code User} or {@code Bot}.
 */
public record GitHubAccount(long databaseId, String login, String typename) {}
