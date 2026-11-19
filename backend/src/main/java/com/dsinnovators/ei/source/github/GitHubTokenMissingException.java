package com.dsinnovators.ei.source.github;

/** No GitHub token is set, so no call is made. */
public class GitHubTokenMissingException extends GitHubApiException {

    private static final long serialVersionUID = 1L;

    GitHubTokenMissingException() {
        super("GITHUB_TOKEN is not set");
    }
}
