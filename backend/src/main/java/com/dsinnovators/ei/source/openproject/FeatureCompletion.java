package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.PullRequestImporter;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 7 of the F03 design: pulls the merged pull requests of every Feature done in a quarter,
 * whatever their date, so that long Features are complete (decisions 61 and 68).
 */
@Component
class FeatureCompletion {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureCompletion.class);

    /** What the job does with the pull requests that were imported: steps 2 to 6 of the design. */
    interface WorkItemImport {
        void importWorkItemsOf(List<RawRecord> pullRequests) throws InterruptedException;
    }

    private final StoredWorkPackages storedWorkPackages;
    private final StoredPullRequests storedPullRequests;
    private final PullRequestImporter pullRequestImporter;

    FeatureCompletion(
            StoredWorkPackages storedWorkPackages,
            StoredPullRequests storedPullRequests,
            PullRequestImporter pullRequestImporter) {
        this.storedWorkPackages = storedWorkPackages;
        this.storedPullRequests = storedPullRequests;
        this.pullRequestImporter = pullRequestImporter;
    }

    /**
     * Imports the merged pull requests of {@code repository} that the Features done in the quarter and
     * the work packages under them list and that are not stored, hands them to {@code workItems}, and
     * repeats while a round stored a new pull request, because they can bring new work packages.
     * Counts {@code features_done_in_quarter}, {@code pull_requests_completed} and
     * {@code pull_requests_failed}.
     *
     * @return the pull requests that could not be imported, as {@code owner/name#number}; each is asked once
     */
    List<String> complete(String repository, Quarter quarter, JobCounts counts, WorkItemImport workItems)
            throws InterruptedException {
        Set<Long> countedFeatures = new HashSet<>();
        Set<Integer> asked = new HashSet<>();
        List<String> failed = new ArrayList<>();
        List<RawRecord> imported;
        do {
            List<Long> features = storedWorkPackages.featuresDoneIn(quarter);
            features.stream()
                    .filter(countedFeatures::add)
                    .forEach(feature -> counts.add("features_done_in_quarter", 1));
            imported = new ArrayList<>();
            for (int number : missingMergedPullRequests(repository, features, asked)) {
                importOne(repository, number, counts, failed).ifPresent(imported::add);
            }
            if (!imported.isEmpty()) {
                counts.add("pull_requests_completed", imported.size());
                workItems.importWorkItemsOf(imported);
            }
        } while (!imported.isEmpty());
        return failed;
    }

    private Set<Integer> missingMergedPullRequests(String repository, List<Long> features, Set<Integer> asked) {
        Set<Integer> missing = new LinkedHashSet<>();
        for (long feature : features) {
            for (long workPackage : storedWorkPackages.numbersUnder(feature)) {
                storedWorkPackages.listedPullRequests(workPackage).stream()
                        .filter(listed -> listed.merged() && repository.equals(listed.repository()))
                        .map(ListedPullRequest::number)
                        .filter(number ->
                                storedPullRequests.find(repository, number).isEmpty())
                        .filter(asked::add)
                        .forEach(missing::add);
            }
        }
        return missing;
    }

    private Optional<RawRecord> importOne(String repository, int number, JobCounts counts, List<String> failed)
            throws InterruptedException {
        try {
            pullRequestImporter.importPullRequest(repository, number, counts);
        } catch (RuntimeException failure) {
            LOG.error("Pull request {}#{} could not be imported", repository, number, failure);
            counts.add("pull_requests_failed", 1);
            failed.add(repository + "#" + number);
            return java.util.Optional.empty();
        }
        return storedPullRequests.find(repository, number);
    }
}
