package com.dsinnovators.ei.api;

class InvalidExchangeLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidExchangeLimitException(String message) {
        super(message);
    }
}
