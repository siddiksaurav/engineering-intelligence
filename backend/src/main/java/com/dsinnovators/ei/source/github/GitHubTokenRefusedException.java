package com.dsinnovators.ei.source.github;

/** GitHub refused the token: wrong, expired or without the right to read. No call can succeed. */
public class GitHubTokenRefusedException extends GitHubApiException {

    private static final long serialVersionUID = 1L;

    GitHubTokenRefusedException(String message) {
        super(message);
    }
}
