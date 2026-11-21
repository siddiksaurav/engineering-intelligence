package com.dsinnovators.ei.judgment;

import java.util.List;

/**
 * The Review comment written items to judge. {@code judgment} defines it and {@code evidence}
 * implements it, so that {@code judgment} never depends on {@code evidence} (decision 166).
 */
public interface ReviewCommentItems {

    /** Every current Review comment written item, withdrawn and superseded rows left out, oldest row first. */
    List<ReviewCommentToJudge> current();
}
