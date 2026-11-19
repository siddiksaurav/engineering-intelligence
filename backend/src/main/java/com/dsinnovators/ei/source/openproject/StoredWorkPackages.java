package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Reads what the OpenProject import stored; the latest version of each record. */
@Component
public class StoredWorkPackages {

    private static final String FEATURE_TYPE = "Feature";
    private static final String DONE_STATUSES_SETTING = "openproject.done_statuses";
    private static final String WORK_PACKAGE_PATH = "/api/v3/work_packages/";

    // The html form of a status change keeps the two names apart even when a name holds the word "to".
    // A status "set" is the first status of a work package or one a workflow sets: also a change
    // into that status (decision 64).
    private static final Pattern STATUS_CHANGE =
            Pattern.compile("<strong>Status</strong> (?:changed from <i>.*?</i> to|set to) <i>(.*)</i>");

    private static final Pattern TRAILING_NUMBER = Pattern.compile("/([0-9]+)$");

    private final RawRecordStore rawRecords;
    private final WorkPackageActivityRecords activityRecords;
    private final PullRequestWorkItemLinkRepository links;
    private final SettingService settings;

    StoredWorkPackages(
            RawRecordStore rawRecords,
            WorkPackageActivityRecords activityRecords,
            PullRequestWorkItemLinkRepository links,
            SettingService settings) {
        this.rawRecords = rawRecords;
        this.activityRecords = activityRecords;
        this.links = links;
        this.settings = settings;
    }

    /** The {@code work_package} record with this number, if it was stored. */
    public Optional<RawRecord> find(long number) {
        return rawRecords.findLatest(
                OpenProjectSource.KEY, OpenProjectRecordKind.WORK_PACKAGE.recordKind(), String.valueOf(number));
    }

    /** Every link found for one pull request, by its F02 external id, in the order they were written. */
    @Transactional(readOnly = true)
    public List<PullRequestWorkItemLink> linksOf(String pullRequestExternalId) {
        return links.findOfPullRequest(pullRequestExternalId);
    }

    /**
     * The work package itself when it is a Feature, else its nearest Feature ancestor. Empty when
     * none is stored: the walk up ends at a work package that is not stored or has no parent.
     */
    public Optional<Long> nearestFeature(long number) {
        Set<Long> visited = new HashSet<>();
        Optional<Long> current = Optional.of(number);
        while (current.isPresent() && visited.add(current.get())) {
            Optional<JsonNode> workPackage = find(current.get()).map(RawRecord::getPayload);
            if (workPackage.isEmpty()) {
                return Optional.empty();
            }
            if (FEATURE_TYPE.equals(
                    workPackage.get().path("_links").path("type").path("title").asString())) {
                return current;
            }
            current = trailingNumber(
                    workPackage.get().path("_links").path("parent").path("href").asString(""));
        }
        return Optional.empty();
    }

    /** The changes of status of a work package, read from its activities, oldest first. */
    @Transactional(readOnly = true)
    public List<StatusChange> statusChanges(long number) {
        return activityRecords
                .findLatestOfWorkPackage(
                        OpenProjectSource.KEY,
                        OpenProjectRecordKind.WORK_PACKAGE_ACTIVITY.recordKind(),
                        WORK_PACKAGE_PATH + number)
                .stream()
                .map(RawRecord::getPayload)
                .flatMap(StoredWorkPackages::statusChangesOf)
                .sorted(Comparator.comparing(StatusChange::at))
                .toList();
    }

    /**
     * Whether the status of the work package changed into a done status within the quarter
     * (decision 64). The done statuses are the setting {@code openproject.done_statuses} of that
     * quarter; names are compared without regard to case. The day of a change is read in UTC.
     */
    public boolean isDoneIn(long number, Quarter quarter) {
        Set<String> doneStatuses = settings.getTextList(DONE_STATUSES_SETTING, quarter).stream()
                .map(status -> status.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return statusChanges(number).stream()
                .filter(change ->
                        quarter.contains(change.at().atOffset(ZoneOffset.UTC).toLocalDate()))
                .anyMatch(change -> doneStatuses.contains(change.status().toLowerCase(Locale.ROOT)));
    }

    private static Stream<StatusChange> statusChangesOf(JsonNode activity) {
        Instant at = Instant.parse(activity.path("createdAt").asString());
        Long userId = trailingNumber(
                        activity.path("_links").path("user").path("href").asString(""))
                .orElse(null);
        return activity.path("details")
                .valueStream()
                .map(detail -> STATUS_CHANGE.matcher(detail.path("html").asString("")))
                .filter(Matcher::matches)
                .map(matcher -> new StatusChange(matcher.group(1), at, userId));
    }

    private static Optional<Long> trailingNumber(String href) {
        Matcher matcher = TRAILING_NUMBER.matcher(href);
        return matcher.find() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }
}
