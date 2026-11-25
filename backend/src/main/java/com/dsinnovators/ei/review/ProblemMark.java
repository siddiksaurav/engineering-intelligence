package com.dsinnovators.ei.review;

/**
 * The user's verdict on one problem the AI listed.
 *
 * @param name the name of the problem, as the judgment gives it
 * @param verdict {@code right}, {@code should_be_routine}, {@code should_be_answer_exists},
 *     {@code should_be_no_ready_answer} or {@code not_a_problem}
 */
public record ProblemMark(String name, String verdict) {}
