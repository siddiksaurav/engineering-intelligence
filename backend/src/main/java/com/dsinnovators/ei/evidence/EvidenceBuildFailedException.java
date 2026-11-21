package com.dsinnovators.ei.evidence;

/** Some evidence items could not be built or written; the others were. */
class EvidenceBuildFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    EvidenceBuildFailedException(String message) {
        super(message);
    }
}
