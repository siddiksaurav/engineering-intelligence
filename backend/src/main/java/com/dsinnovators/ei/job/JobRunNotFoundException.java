package com.dsinnovators.ei.job;

/** A run was asked for by an id that no run has. */
class JobRunNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    JobRunNotFoundException(long runId) {
        super("No job run with id " + runId);
    }
}
