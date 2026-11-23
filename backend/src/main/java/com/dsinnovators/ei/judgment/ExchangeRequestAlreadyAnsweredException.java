package com.dsinnovators.ei.judgment;

/** The model exchange request has an answer already; a second one is refused. */
public class ExchangeRequestAlreadyAnsweredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ExchangeRequestAlreadyAnsweredException(String message) {
        super(message);
    }
}
