package com.dsinnovators.ei.judgment;

/** Some pull requests could not be digested; the others were. */
class PullRequestDigestFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    PullRequestDigestFailedException(String message) {
        super(message);
    }
}
