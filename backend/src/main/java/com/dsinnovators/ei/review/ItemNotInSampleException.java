package com.dsinnovators.ei.review;

/** The evidence item is not in the blind check sample. */
public class ItemNotInSampleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ItemNotInSampleException(long evidenceItemId) {
        super("Evidence item " + evidenceItemId + " is not in the blind check sample");
    }
}
