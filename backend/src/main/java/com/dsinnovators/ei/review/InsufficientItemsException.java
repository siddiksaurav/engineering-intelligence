package com.dsinnovators.ei.review;

/** The sample cannot be picked: a complexity has fewer items than the setting asks for. */
public class InsufficientItemsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InsufficientItemsException(String complexity, int available, int wanted) {
        super("The blind check needs " + wanted + " Feature work items judged " + complexity + ", but only " + available
                + " exist");
    }
}
