package com.dsinnovators.ei.judgment;

/** The model's answer does not follow the answer schema of its prompt. The job asks again once (decision 135). */
class InvalidAnswerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidAnswerException(String message) {
        super(message);
    }
}
