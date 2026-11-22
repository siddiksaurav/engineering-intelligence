package com.dsinnovators.ei.scoring;

/** Whether an evidence item has a tier it can be counted with. */
public enum JudgmentState {
    /** The item has a current judgment with a tier. */
    JUDGED,
    /** The item has no current judgment yet (decision 218). */
    NOT_JUDGED_YET,
    /** A review comment judged as not substantive, so it has no tier (decision 219). */
    NOT_SUBSTANTIVE
}
