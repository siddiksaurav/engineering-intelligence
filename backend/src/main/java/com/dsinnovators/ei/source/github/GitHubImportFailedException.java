package com.dsinnovators.ei.source.github;

/** Some pull requests of an import could not be fetched or stored; the others were. */
public class GitHubImportFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    GitHubImportFailedException(String message) {
        super(message);
    }
}
