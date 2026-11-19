package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.GitHubImportArguments.MergeRange;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.openproject.PullRequestWorkItemLinks.Found;
import com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Links stored pull requests to the OpenProject work items they name and stores those work items
 * with what hangs on them, then completes the Features done in a quarter (F03 design, "Steps").
 * Running it again stores only what changed.
 */
@Component
class OpenProjectImportJob implements Job {

    private static final int FAILED_NUMBERS_SHOWN = 20;

    private final StoredPullRequests storedPullRequests;
    private final OpenProjectSource source;
    private final WorkPackageReferenceResolver resolver;
    private final WorkPackageCollector collector;
    private final WorkPackageImporter importer;
    private final PullRequestWorkItemLinks links;
    private final FeatureCompletion completion;

    OpenProjectImportJob(
            StoredPullRequests storedPullRequests,
            OpenProjectSource source,
            WorkPackageReferenceResolver resolver,
            WorkPackageCollector collector,
            WorkPackageImporter importer,
            PullRequestWorkItemLinks links,
            FeatureCompletion completion) {
        this.storedPullRequests = storedPullRequests;
        this.source = source;
        this.resolver = resolver;
        this.collector = collector;
        this.importer = importer;
        this.links = links;
        this.completion = completion;
    }

    @Override
    public String name() {
        return "openproject-import";
    }

    /** Refuses arguments that are wrong, and a pull request number that was not stored by {@code github-import}. */
    @Override
    public void checkArguments(JobArguments arguments) {
        OpenProjectImportArguments importArguments = OpenProjectImportArguments.parse(arguments);
        for (int number : importArguments.pullRequestNumbers()) {
            storedPullRequest(importArguments.repository(), number);
        }
    }

    /**
     * Fails at once when a call that lists work packages fails. A work package or a completion pull
     * request that fails on its own is counted and the run goes on; the run then fails at the end,
     * naming them.
     */
    @Override
    public void run(JobArguments arguments, JobCounts counts) throws InterruptedException {
        OpenProjectImportArguments importArguments = OpenProjectImportArguments.parse(arguments);
        List<RawRecord> pullRequests = selectedPullRequests(importArguments);
        counts.add("pull_requests_read", pullRequests.size());
        List<WorkPackageImporter.Imported> imports = new ArrayList<>();
        imports.add(importWorkItemsOf(pullRequests, counts));
        List<String> failedPullRequests = completion.complete(
                importArguments.repository(),
                importArguments.quarter(),
                counts,
                completed -> imports.add(importWorkItemsOf(completed, counts)));
        failIfSomethingFailed(imports, failedPullRequests);
    }

    private static void failIfSomethingFailed(
            List<WorkPackageImporter.Imported> imports, List<String> failedPullRequests) {
        List<Long> failedNumbers = imports.stream()
                .flatMap(imported -> imported.failedNumbers().stream())
                .toList();
        int total =
                imports.stream().mapToInt(WorkPackageImporter.Imported::total).sum();
        List<String> problems = new ArrayList<>();
        if (!failedNumbers.isEmpty()) {
            problems.add(failedNumbers.size() + " of " + total + " work packages failed: "
                    + shown(failedNumbers.stream().map(number -> "#" + number).toList()));
        }
        if (!failedPullRequests.isEmpty()) {
            problems.add(failedPullRequests.size() + " completion pull requests failed: " + shown(failedPullRequests));
        }
        if (!problems.isEmpty()) {
            throw new OpenProjectImportFailedException(String.join("; ", problems));
        }
    }

    private static String shown(List<String> names) {
        return names.stream().limit(FAILED_NUMBERS_SHOWN).collect(Collectors.joining(", "));
    }

    private List<RawRecord> selectedPullRequests(OpenProjectImportArguments arguments) {
        if (arguments.mergeRange().isPresent()) {
            MergeRange range = arguments.mergeRange().get();
            return storedPullRequests.mergedBetween(arguments.repository(), range.from(), range.to());
        }
        return arguments.pullRequestNumbers().stream()
                .map(number -> storedPullRequest(arguments.repository(), number))
                .toList();
    }

    private RawRecord storedPullRequest(String repository, int number) {
        return storedPullRequests
                .find(repository, number)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pull request " + repository + "#" + number + " is not stored; run github-import first"));
    }

    /**
     * Steps 2 to 6 for these pull requests: finds and resolves their references, stores the work packages
     * behind them and writes their links.
     */
    private WorkPackageImporter.Imported importWorkItemsOf(List<RawRecord> pullRequests, JobCounts counts)
            throws InterruptedException {
        WorkPackageReferenceFinder finder =
                new WorkPackageReferenceFinder(URI.create(source.dataSource().getBaseUrl()));
        Map<String, List<WorkPackageReference>> referencesByPullRequest = new LinkedHashMap<>();
        for (RawRecord pullRequest : pullRequests) {
            referencesByPullRequest.put(
                    pullRequest.getExternalId(),
                    finder.find(text(pullRequest.getPayload(), "title"), text(pullRequest.getPayload(), "body")));
        }
        Map<String, ResolvedReference> resolved = resolve(referencesByPullRequest.values(), counts);

        Map<Long, JsonNode> collected = collector.collect(resolved.values().stream()
                .filter(ResolvedReference::visible)
                .map(ResolvedReference::workPackageId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        WorkPackageImporter.Imported imported = importer.importAll(collected.values(), counts);

        Map<String, List<Found>> openProjectSide = linksListedByOpenProject(imported.stored());
        for (Map.Entry<String, List<WorkPackageReference>> entry : referencesByPullRequest.entrySet()) {
            List<Found> found = new ArrayList<>();
            for (WorkPackageReference reference : entry.getValue()) {
                found.add(new Found(resolved.get(reference.reference()), reference.foundIn()));
            }
            found.addAll(openProjectSide.getOrDefault(entry.getKey(), List.of()));
            links.replace(entry.getKey(), found);
            if (!found.isEmpty()) {
                counts.add("pull_requests_linked", 1);
            }
        }
        return imported;
    }

    private Map<String, ResolvedReference> resolve(Collection<List<WorkPackageReference>> references, JobCounts counts)
            throws InterruptedException {
        Set<String> distinct = references.stream()
                .flatMap(List::stream)
                .map(WorkPackageReference::reference)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ResolvedReference> resolved = resolver.resolve(distinct).stream()
                .collect(Collectors.toMap(
                        ResolvedReference::reference,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));
        long visible =
                resolved.values().stream().filter(ResolvedReference::visible).count();
        counts.add("references_found", resolved.size());
        counts.add("references_resolved", visible);
        counts.add("references_not_visible", resolved.size() - visible);
        return resolved;
    }

    /** The links that OpenProject shows on the stored work packages, by the pull request they name. */
    private static Map<String, List<Found>> linksListedByOpenProject(List<FetchedWorkPackage> stored) {
        Map<String, List<Found>> found = new LinkedHashMap<>();
        for (FetchedWorkPackage fetched : stored) {
            for (String pullRequestExternalId : fetched.linkedPullRequestExternalIds()) {
                found.computeIfAbsent(pullRequestExternalId, key -> new ArrayList<>())
                        .add(new Found(
                                ResolvedReference.resolved(String.valueOf(fetched.number()), fetched.number()),
                                FoundIn.OPENPROJECT));
            }
        }
        return found;
    }

    private static String text(JsonNode pullRequest, String field) {
        JsonNode value = pullRequest.path(field);
        return value.isString() ? value.asString() : "";
    }
}
