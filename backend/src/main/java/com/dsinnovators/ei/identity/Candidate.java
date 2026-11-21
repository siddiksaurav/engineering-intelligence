package com.dsinnovators.ei.identity;

import java.util.List;

/**
 * An OpenProject user who may be the person behind a GitHub account, with the evidence for it.
 *
 * @param userId the OpenProject user id
 * @param name the name OpenProject shows for the user
 * @param supportingItems linked work items whose "Developers" names the user
 * @param statusChangeItems linked work items whose status the user changed
 * @param personItems work items linked from the person's pull requests
 * @param evidence one entry per supporting work item and one per status change
 */
public record Candidate(
        long userId,
        String name,
        int supportingItems,
        int statusChangeItems,
        int personItems,
        List<EvidenceEntry> evidence) {}
