package com.dsinnovators.ei.source.openproject;

/** A call to OpenProject failed. The message says which call and why. */
public class OpenProjectApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    OpenProjectApiException(String message) {
        super(message);
    }

    OpenProjectApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
