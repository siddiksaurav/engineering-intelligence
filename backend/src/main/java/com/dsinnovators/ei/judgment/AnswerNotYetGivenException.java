package com.dsinnovators.ei.judgment;

/**
 * The model exchange holds no answer for the request yet. The request now waits in the exchange; the
 * job counts the subject as left for the exchange and goes on with the next one.
 */
class AnswerNotYetGivenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    AnswerNotYetGivenException(String message) {
        super(message);
    }
}
