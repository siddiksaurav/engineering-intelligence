package com.dsinnovators.ei.source.openproject;

/**
 * What the evidence builders read from a stored work package.
 *
 * @param rawRecordId the exact record version the facts were read from
 * @param type the tracker type name, such as {@code Feature} or {@code Bug}
 * @param isBug whether the type is Bug; a Bug is never Feature work
 * @param projectId the {@code project} table id of the work package's project; null when that project is not stored
 * @param priority the priority name; null when the work package has none
 * @param url the work package on OpenProject
 */
public record WorkPackageFacts(
        long rawRecordId,
        long number,
        String title,
        String type,
        boolean isBug,
        Long projectId,
        String priority,
        String url) {}
