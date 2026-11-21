package com.dsinnovators.ei.identity;

import com.dsinnovators.ei.identity.EvidenceEntry.Signal;
import com.dsinnovators.ei.source.DataSourceRepository;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.openproject.PullRequestWorkItemLink;
import com.dsinnovators.ei.source.openproject.StatusChange;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The OpenProject users who may be the person behind a set of pull requests (F04 design,
 * "Candidates"). A user is a candidate when the "Developers" field of a linked work item names
 * them; a user seen only in status changes is not (decision 93). Groups are ignored (decision 86).
 */
@Component
public class OpenProjectCandidates {

    private static final String DEVELOPERS_FIELD = "customField270";
    private static final String USER_HREF_PREFIX = "/api/v3/users/";
    private static final String WORK_ITEM_PATH = "/wp/";

    private final StoredWorkPackages workPackages;
    private final DataSourceRepository dataSources;

    OpenProjectCandidates(StoredWorkPackages workPackages, DataSourceRepository dataSources) {
        this.workPackages = workPackages;
        this.dataSources = dataSources;
    }

    /** The candidates of the pull requests with these external ids, ordered by user id. */
    public List<Candidate> of(List<String> pullRequestExternalIds) {
        Set<Long> items = linkedWorkItems(pullRequestExternalIds);
        Map<Long, CandidateBuilder> builders = new TreeMap<>();
        for (long item : items) {
            workPackages
                    .find(item)
                    .map(RawRecord::getPayload)
                    .ifPresent(payload -> addDevelopers(builders, item, payload));
        }
        for (long item : items) {
            addStatusChanges(builders, item);
        }
        return builders.values().stream()
                .map(builder -> builder.build(items.size()))
                .toList();
    }

    private Set<Long> linkedWorkItems(List<String> pullRequestExternalIds) {
        Set<Long> items = new LinkedHashSet<>();
        for (String pullRequest : pullRequestExternalIds) {
            workPackages.linksOf(pullRequest).stream()
                    .filter(PullRequestWorkItemLink::isVisible)
                    .map(PullRequestWorkItemLink::getWorkPackageId)
                    .forEach(items::add);
        }
        return items;
    }

    private void addDevelopers(Map<Long, CandidateBuilder> builders, long item, JsonNode workPackage) {
        Set<Long> namedOnThisItem = new LinkedHashSet<>();
        for (JsonNode developer : workPackage.path("_links").path(DEVELOPERS_FIELD)) {
            userId(developer.path("href").asString(""))
                    .filter(namedOnThisItem::add)
                    .ifPresent(userId -> builders.computeIfAbsent(
                                    userId,
                                    id -> new CandidateBuilder(
                                            id, developer.path("title").asString("")))
                            .addSupportingItem(item));
        }
    }

    private void addStatusChanges(Map<Long, CandidateBuilder> builders, long item) {
        for (StatusChange change : workPackages.statusChanges(item)) {
            CandidateBuilder builder = change.userId() == null ? null : builders.get(change.userId());
            if (builder != null) {
                builder.addStatusChange(item, change);
            }
        }
    }

    private static Optional<Long> userId(String href) {
        if (!href.startsWith(USER_HREF_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(href.substring(USER_HREF_PREFIX.length())));
        } catch (NumberFormatException notAUserId) {
            return Optional.empty();
        }
    }

    private String workItemUrl(long number) {
        String baseUrl = dataSources
                .findByKey(IdentitySource.OPENPROJECT)
                .orElseThrow(() -> new IllegalStateException(
                        "The data source " + IdentitySource.OPENPROJECT + " is not in the database"))
                .getBaseUrl();
        return baseUrl + WORK_ITEM_PATH + number;
    }

    /** Collects what one user is seen doing on the linked work items. */
    private final class CandidateBuilder {

        private final long userId;
        private final String name;
        private final Set<Long> supportingItems = new LinkedHashSet<>();
        private final Set<Long> statusChangeItems = new LinkedHashSet<>();
        private final List<EvidenceEntry> supportingEvidence = new ArrayList<>();
        private final List<EvidenceEntry> statusChangeEvidence = new ArrayList<>();

        CandidateBuilder(long userId, String name) {
            this.userId = userId;
            this.name = name;
        }

        void addSupportingItem(long item) {
            supportingItems.add(item);
            supportingEvidence.add(new EvidenceEntry(item, workItemUrl(item), Signal.DEVELOPERS, null));
        }

        void addStatusChange(long item, StatusChange change) {
            statusChangeItems.add(item);
            statusChangeEvidence.add(new EvidenceEntry(item, workItemUrl(item), Signal.STATUS_CHANGE, change.at()));
        }

        Candidate build(int personItems) {
            List<EvidenceEntry> evidence = new ArrayList<>(supportingEvidence);
            evidence.addAll(statusChangeEvidence);
            return new Candidate(
                    userId, name, supportingItems.size(), statusChangeItems.size(), personItems, List.copyOf(evidence));
        }
    }
}
