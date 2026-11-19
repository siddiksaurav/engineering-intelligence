package com.dsinnovators.ei.source.github;

/** GitHub has nothing at what was asked for, for example a pull request number that does not exist. */
public class GitHubNotFoundException extends GitHubApiException {

    private static final long serialVersionUID = 1L;

    GitHubNotFoundException(String message) {
        super(message);
    }
}
