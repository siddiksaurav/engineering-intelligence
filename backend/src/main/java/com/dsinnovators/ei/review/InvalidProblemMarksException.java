package com.dsinnovators.ei.review;

/** The problem marks do not match the problems of the judgment the sample fixed. */
public class InvalidProblemMarksException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidProblemMarksException(String message) {
        super(message);
    }
}
