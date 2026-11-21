package com.dsinnovators.ei.judgment;

/** Some items of a Stage 2 job could not be judged; the others were. */
class JudgmentJobFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    JudgmentJobFailedException(String message) {
        super(message);
    }
}
