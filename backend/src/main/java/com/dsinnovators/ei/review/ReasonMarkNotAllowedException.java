package com.dsinnovators.ei.review;

/** The AI's reason cannot be marked yet, or at all: the item has no label, or the user could not judge it. */
public class ReasonMarkNotAllowedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ReasonMarkNotAllowedException(String message) {
        super(message);
    }
}
