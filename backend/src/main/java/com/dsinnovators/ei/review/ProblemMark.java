package com.dsinnovators.ei.review;

import java.util.List;

/**
 * The user's verdict on one problem the AI listed.
 *
 * @param name the name of the problem, as the judgment gives it
 * @param verdict {@code right}, {@code should_be_routine}, {@code should_be_answer_exists},
 *     {@code should_be_no_ready_answer} or {@code not_a_problem}
 */
public record ProblemMark(String name, String verdict) {

    /** The verdicts a problem can get, in the order the numbers list them. */
    public static final List<String> VERDICTS = List.of(
            "right", "should_be_routine", "should_be_answer_exists", "should_be_no_ready_answer", "not_a_problem");
}
