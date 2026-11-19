package com.dsinnovators.ei.source.github;

/** A call to GitHub failed. The message says which call and why; it never holds the token. */
public class GitHubApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    GitHubApiException(String message) {
        super(message);
    }

    GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
