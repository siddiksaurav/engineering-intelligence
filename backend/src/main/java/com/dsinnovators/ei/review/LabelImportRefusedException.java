package com.dsinnovators.ei.review;

import java.util.List;

/** A label file was refused as a whole; nothing was stored. */
public class LabelImportRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<LabelProblem> problems;

    LabelImportRefusedException(String message, List<LabelProblem> problems) {
        super(message);
        this.problems = List.copyOf(problems);
    }

    /** One entry per wrong row; empty when the file as a whole is unreadable. */
    public List<LabelProblem> problems() {
        return problems;
    }
}
