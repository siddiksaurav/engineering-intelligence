package com.dsinnovators.ei.identity;

/** Some GitHub accounts could not be turned into persons or proposals; the others were. */
class IdentityBuildFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    IdentityBuildFailedException(String message) {
        super(message);
    }
}
