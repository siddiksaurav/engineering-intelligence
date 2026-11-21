package com.dsinnovators.ei.judgment;

/**
 * Every provider has reached its limit for this run, or none is configured. The run stops; the next
 * run goes on with what is left (decision 146).
 */
public class ModelLimitReachedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ModelLimitReachedException(String message) {
        super(message);
    }
}
