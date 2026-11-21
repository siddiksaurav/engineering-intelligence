package com.dsinnovators.ei.judgment;

import java.util.List;

/**
 * The Feature work items to judge. {@code judgment} defines it and {@code evidence} implements it, so
 * that {@code judgment} never depends on {@code evidence} (decision 166).
 */
public interface FeatureWorkItems {

    /** Every current Feature work item, withdrawn and superseded rows left out, oldest row first. */
    List<FeatureWorkToJudge> current();
}
