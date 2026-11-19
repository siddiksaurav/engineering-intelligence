package com.dsinnovators.ei.source.openproject;

import java.util.Locale;

/**
 * A work item named in a pull request, as the pull request writes it.
 *
 * @param reference a work item number ({@code 56343}) or a key in upper case ({@code OP-19345})
 * @param foundIn the part of the pull request that names it
 */
public record WorkPackageReference(String reference, FoundIn foundIn) {

    /** The part of a pull request a reference is read from (decision 57). */
    public enum FoundIn {
        PULL_REQUEST_BODY,
        PULL_REQUEST_TITLE
    }

    public WorkPackageReference {
        reference = reference.toUpperCase(Locale.ROOT);
    }
}
