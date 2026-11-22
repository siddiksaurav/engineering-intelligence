package com.dsinnovators.ei.api;

class InvalidQuarterException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidQuarterException(String message, Throwable cause) {
        super(message, cause);
    }

    InvalidQuarterException(String message) {
        super(message);
    }
}
