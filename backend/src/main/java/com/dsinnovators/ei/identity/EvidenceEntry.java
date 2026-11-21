package com.dsinnovators.ei.identity;

import java.time.Instant;

/**
 * One reason for a proposal: a work item linked from the person's pull requests that names the
 * OpenProject user in "Developers", or whose status that user changed.
 *
 * @param workPackage the work package number
 * @param url the work item on the OpenProject site
 * @param signal what ties the user to the work item
 * @param at when the status changed; {@code null} for the {@link Signal#DEVELOPERS} signal
 */
public record EvidenceEntry(long workPackage, String url, Signal signal, Instant at) {

    /** What ties an OpenProject user to a work item. */
    public enum Signal {
        DEVELOPERS,
        STATUS_CHANGE
    }
}
