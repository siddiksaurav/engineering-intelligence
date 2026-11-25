package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.LabelProblem;
import java.util.List;

/** The answer to a refused label file: one entry per wrong row, none when the header is unreadable. */
record LabelImportRefusalResponse(String message, List<LabelProblem> problems) {}
