package com.dsinnovators.ei.api;

import com.dsinnovators.ei.common.Quarter;

/** Reads the required {@code quarter} request parameter; a missing or malformed value is refused. */
final class QuarterParameter {

    private QuarterParameter() {}

    static Quarter parse(String quarterText) {
        if (quarterText == null) {
            throw new InvalidQuarterException("The parameter quarter is required, in the form YYYY-Qn");
        }
        try {
            return Quarter.parse(quarterText);
        } catch (IllegalArgumentException wrongForm) {
            throw new InvalidQuarterException(wrongForm.getMessage(), wrongForm);
        }
    }
}
