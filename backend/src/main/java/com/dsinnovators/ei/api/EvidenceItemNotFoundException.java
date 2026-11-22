package com.dsinnovators.ei.api;

class EvidenceItemNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    EvidenceItemNotFoundException(long evidenceItemId) {
        super("No evidence item with id " + evidenceItemId);
    }
}
