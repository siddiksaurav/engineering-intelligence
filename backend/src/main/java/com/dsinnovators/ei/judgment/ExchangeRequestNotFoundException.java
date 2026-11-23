package com.dsinnovators.ei.judgment;

/** No model exchange request has the given hash. */
public class ExchangeRequestNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ExchangeRequestNotFoundException(String message) {
        super(message);
    }
}
