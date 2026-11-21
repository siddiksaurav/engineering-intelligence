package com.dsinnovators.ei.judgment;

/** The model's answer does not follow the digest schema. The job asks again once (decision 135). */
class InvalidDigestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidDigestException(String message) {
        super(message);
    }
}
