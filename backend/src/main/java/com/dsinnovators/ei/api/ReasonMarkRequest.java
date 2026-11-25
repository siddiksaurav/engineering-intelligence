package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.ProblemMark;
import java.util.List;

/**
 * The user's verdict on the AI's reason for one item.
 *
 * @param reasonRight whether the AI's reason is factually right
 * @param problemMarks one entry per problem of the judgment, in its order
 * @param note the user's own words, optional
 */
record ReasonMarkRequest(Boolean reasonRight, List<ProblemMark> problemMarks, String note) {

    /** @throws InvalidReasonMarkRequestException when a required field is missing */
    void requireComplete() {
        if (reasonRight == null) {
            throw new InvalidReasonMarkRequestException("reasonRight is required: true or false");
        }
        if (problemMarks == null
                || problemMarks.stream()
                        .anyMatch(mark -> mark == null || mark.name() == null || mark.verdict() == null)) {
            throw new InvalidReasonMarkRequestException(
                    "problemMarks is required: a list with a name and a verdict for each problem of the judgment");
        }
    }
}
