package com.dsinnovators.ei.judgment;

/** A call to a model failed, or its answer could not be read. The message never holds a key. */
public class ModelCallException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ModelCallException(String message) {
        super(message);
    }

    ModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
