package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/**
 * The link between a stored pull request and a work item, as one reference found it. Derived
 * data, not a source copy: {@link PullRequestWorkItemLinks} replaces the rows of a pull request.
 */
@Entity
@Table(name = "pull_request_work_item")
public class PullRequestWorkItemLink {

    private static final String RESOLVED = "resolved";
    private static final String NOT_VISIBLE = "not_visible";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pullRequestExternalId;

    private Long workPackageId;

    private String reference;

    private String foundIn;

    private String state;

    private Instant linkedAt;

    protected PullRequestWorkItemLink() {}

    PullRequestWorkItemLink(
            String pullRequestExternalId, ResolvedReference resolved, FoundIn foundIn, Instant linkedAt) {
        this.pullRequestExternalId = pullRequestExternalId;
        this.workPackageId = resolved.workPackageId();
        this.reference = resolved.reference();
        this.foundIn = foundIn.name().toLowerCase(Locale.ROOT);
        this.state = resolved.visible() ? RESOLVED : NOT_VISIBLE;
        this.linkedAt = linkedAt;
    }

    // Read by F05, which lists the links of a pull request; not yet by production code.
    public String getPullRequestExternalId() {
        return pullRequestExternalId;
    }

    /** The work package number; {@code null} when the public API does not show the item. */
    public Long getWorkPackageId() {
        return workPackageId;
    }

    // Read by F05, which shows the reference as the author wrote it; not yet by production code.
    /** The reference as written: a key such as {@code FND-101}, or a number. */
    public String getReference() {
        return reference;
    }

    public FoundIn getFoundIn() {
        return FoundIn.valueOf(foundIn.toUpperCase(Locale.ROOT));
    }

    public boolean isVisible() {
        return RESOLVED.equals(state);
    }

    // Read by F05, which orders links by when they were found; not yet by production code.
    public Instant getLinkedAt() {
        return linkedAt;
    }
}
