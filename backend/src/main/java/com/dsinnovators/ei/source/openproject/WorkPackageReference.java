package com.dsinnovators.ei.source.openproject;

import java.util.Locale;

/**
 * A work item named in a pull request, as the pull request writes it.
 *
 * @param reference a work item number ({@code 56343}) or a key in upper case ({@code OP-19345})
 * @param foundIn the part of the pull request that names it
 */
public record WorkPackageReference(String reference, FoundIn foundIn) {

    /**
     * Where a link between a pull request and a work item was found: a part of the pull request
     * (decision 57), or the list of pull requests OpenProject shows on the work item. The lower
     * case name is the value of {@code pull_request_work_item.found_in}.
     */
    public enum FoundIn {
        PULL_REQUEST_BODY,
        PULL_REQUEST_TITLE,
        OPENPROJECT
    }

    public WorkPackageReference {
        reference = reference.toUpperCase(Locale.ROOT);
    }
}
