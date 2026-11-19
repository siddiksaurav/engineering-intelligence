package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.job.JobCounts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Fetches and stores collected work packages with their activities and pull request lists, then
 * the projects they sit in, each project once (F03 design, step 5).
 */
@Component
class WorkPackageImporter {

    private static final Logger LOG = LoggerFactory.getLogger(WorkPackageImporter.class);

    private final WorkPackageFetcher fetcher;
    private final WorkPackageRecorder workPackageRecorder;
    private final ProjectRecorder projectRecorder;

    WorkPackageImporter(
            WorkPackageFetcher fetcher, WorkPackageRecorder workPackageRecorder, ProjectRecorder projectRecorder) {
        this.fetcher = fetcher;
        this.workPackageRecorder = workPackageRecorder;
        this.projectRecorder = projectRecorder;
    }

    /**
     * What an import stored and what failed.
     *
     * @param stored the work packages that were stored or found unchanged
     * @param failedNumbers the work packages that could not be fetched or stored
     */
    record Imported(List<FetchedWorkPackage> stored, List<Long> failedNumbers) {}

    /**
     * Counts {@code <kind>.<outcome>} for each stored record and {@code work_packages_failed}. A work
     * package that fails is logged and left out, the others go on. A failing project call fails the
     * import at once.
     *
     * @throws OpenProjectApiException when a project call fails after its retries
     */
    Imported importAll(Collection<JsonNode> workPackages, JobCounts counts) throws InterruptedException {
        List<FetchedWorkPackage> stored = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        for (JsonNode workPackage : workPackages) {
            long number = workPackage.path("id").asLong();
            try {
                FetchedWorkPackage fetched = fetcher.fetchWithDetails(workPackage);
                count(workPackageRecorder.store(fetched), counts);
                stored.add(fetched);
            } catch (RuntimeException failure) {
                LOG.error("Work package {} could not be imported", number, failure);
                counts.add("work_packages_failed", 1);
                failed.add(number);
            }
        }
        storeProjectsOf(stored, counts);
        return new Imported(stored, failed);
    }

    private void storeProjectsOf(List<FetchedWorkPackage> stored, JobCounts counts) throws InterruptedException {
        Set<Long> projectIds = new LinkedHashSet<>();
        for (FetchedWorkPackage fetched : stored) {
            HrefNumbers.trailingNumber(fetched.workPackage()
                            .path("_links")
                            .path("project")
                            .path("href")
                            .asString(""))
                    .ifPresent(projectIds::add);
        }
        for (long projectId : projectIds) {
            count(List.of(projectRecorder.store(fetcher.fetchProject(projectId))), counts);
        }
    }

    private static void count(List<StoredRecord> records, JobCounts counts) {
        for (StoredRecord record : records) {
            counts.add(record.outcome().countName(record.kind().recordKind()), 1);
        }
    }
}
