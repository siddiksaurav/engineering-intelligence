package com.dsinnovators.ei.api;

class InvalidEvidenceItemIdsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidEvidenceItemIdsException(String message) {
        super(message);
    }
}
