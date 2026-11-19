package com.dsinnovators.ei.source.openproject;

/**
 * What the server made of one reference to a work item.
 *
 * @param reference the reference as the pull request wrote it
 * @param workPackageId the work package number, or {@code null} when the public API does not show the item
 */
public record ResolvedReference(String reference, Long workPackageId) {

    static ResolvedReference resolved(String reference, long workPackageId) {
        return new ResolvedReference(reference, workPackageId);
    }

    static ResolvedReference notVisible(String reference) {
        return new ResolvedReference(reference, null);
    }

    public boolean visible() {
        return workPackageId != null;
    }
}
