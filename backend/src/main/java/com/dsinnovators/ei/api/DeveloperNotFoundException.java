package com.dsinnovators.ei.api;

class DeveloperNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DeveloperNotFoundException(long personId) {
        super("No developer with id " + personId);
    }
}
