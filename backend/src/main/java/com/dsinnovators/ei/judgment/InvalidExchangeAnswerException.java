package com.dsinnovators.ei.judgment;

/** An answer given to the model exchange is not a JSON object or names no author. */
public class InvalidExchangeAnswerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidExchangeAnswerException(String message) {
        super(message);
    }
}
