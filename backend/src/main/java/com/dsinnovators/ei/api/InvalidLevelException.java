package com.dsinnovators.ei.api;

class InvalidLevelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidLevelException(String message) {
        super(message);
    }
}
