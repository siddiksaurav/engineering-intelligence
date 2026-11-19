package com.dsinnovators.ei.source.openproject;

import java.time.Instant;

/**
 * A change of the status of a work package, read from its activities.
 *
 * @param status the status name the work package changed into
 * @param at when the activity was created
 * @param userId the OpenProject user who made the change, or {@code null} when the activity names none
 */
public record StatusChange(String status, Instant at, Long userId) {}
