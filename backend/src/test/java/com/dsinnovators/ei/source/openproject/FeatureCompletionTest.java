package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.PullRequestImporter;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class FeatureCompletionTest extends DatabaseTest {

    private static final String REPOSITORY = "opf/openproject";
    private static final Quarter Q3 = Quarter.parse("2026-Q3");

    @Autowired
    private StoredWorkPackages storedWorkPackages;

    @Autowired
    private StoredPullRequests storedPullRequests;

    @Autowired
    private WorkPackageRecorder recorder;

    @Autowired
    private RawRecordStore store;

    private final PullRequestImporter pullRequestImporter = mock(PullRequestImporter.class);
    private final Map<String, Long> counts = new TreeMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final List<List<Integer>> rounds = new ArrayList<>();

    private FeatureCompletion completion() {
        return new FeatureCompletion(storedWorkPackages, storedPullRequests, pullRequestImporter);
    }

    private static JsonNode json(String text) {
        return CanonicalJson.parse(text);
    }

    private static String listed(String repository, int number, boolean merged) {
        return "{\"repository\":\"" + repository + "\",\"number\":" + number + ",\"merged\":" + merged + "}";
    }

    private static String statusChange(long number, String status, String createdAt) {
        return "{\"id\":" + number + "1,\"createdAt\":\"" + createdAt
                + "\",\"details\":[{\"html\":\"<strong>Status</strong> changed from <i>new</i> to <i>" + status
                + "</i>\"}],\"_links\":{\"workPackage\":{\"href\":\"/api/v3/work_packages/" + number
                + "\"},\"user\":{\"href\":\"/api/v3/users/7\"}}}";
    }

    private void storeWorkPackage(long number, String type, Long ancestor, String activity, String... pullRequests) {
        String ancestors = ancestor == null ? "[]" : "[{\"href\":\"/api/v3/work_packages/" + ancestor + "\"}]";
        recorder.store(new FetchedWorkPackage(
                number,
                json("{\"id\":" + number + ",\"_links\":{\"type\":{\"title\":\"" + type + "\"},\"ancestors\":"
                        + ancestors + "}}"),
                json("{\"_embedded\":{\"elements\":[" + activity + "]}}"),
                json("{\"reduced\":true,\"pullRequests\":[" + String.join(",", pullRequests) + "]}")));
    }

    private void storeFeatureDone(long number, String createdAt, String... pullRequests) {
        storeWorkPackage(number, "Feature", null, statusChange(number, "closed", createdAt), pullRequests);
    }

    private void storePullRequest(int number) {
        store.store(
                "github",
                "pull_request",
                REPOSITORY + "#" + number,
                "https://github.com/" + REPOSITORY + "/pull/" + number,
                json("{\"number\":" + number + ",\"title\":\"Pull request " + number
                        + "\",\"body\":\"\",\"mergedAt\":\"2026-02-01T08:00:00Z\"}"));
    }

    private void importerStores(int... numbers) throws InterruptedException {
        for (int number : numbers) {
            doAnswer(call -> {
                        storePullRequest(number);
                        return null;
                    })
                    .when(pullRequestImporter)
                    .importPullRequest(eq(REPOSITORY), eq(number), any());
        }
    }

    private List<String> complete() throws InterruptedException {
        return complete(() -> {});
    }

    private List<String> complete(Runnable afterEachRound) throws InterruptedException {
        return completion().complete(REPOSITORY, Q3, jobCounts, pullRequests -> {
            rounds.add(pullRequests.stream()
                    .map(RawRecord::getExternalId)
                    .map(externalId -> Integer.parseInt(externalId.substring(externalId.indexOf('#') + 1)))
                    .toList());
            afterEachRound.run();
        });
    }

    @Test
    void importsTheMissingMergedPullRequestsOfAFeatureDoneInTheQuarterOnly() throws Exception {
        storePullRequest(4);
        storeFeatureDone(
                100,
                "2026-09-20T08:00:00Z",
                listed(REPOSITORY, 1, true),
                listed(REPOSITORY, 2, false),
                listed("opf/other", 3, true),
                listed(REPOSITORY, 4, true));
        storeWorkPackage(101, "Task", 100L, "", listed(REPOSITORY, 5, true));
        storeFeatureDone(200, "2026-10-02T08:00:00Z", listed(REPOSITORY, 6, true));
        importerStores(1, 5);

        List<String> failed = complete();

        assertEquals(List.of(), failed);
        verify(pullRequestImporter).importPullRequest(eq(REPOSITORY), eq(1), any());
        verify(pullRequestImporter).importPullRequest(eq(REPOSITORY), eq(5), any());
        verify(pullRequestImporter, never()).importPullRequest(anyString(), eq(2), any());
        verify(pullRequestImporter, never()).importPullRequest(anyString(), eq(3), any());
        verify(pullRequestImporter, never()).importPullRequest(anyString(), eq(4), any());
        verify(pullRequestImporter, never()).importPullRequest(anyString(), eq(6), any());
        assertEquals(List.of(List.of(1, 5)), rounds);
        assertEquals(1L, counts.get("features_done_in_quarter"));
        assertEquals(2L, counts.get("pull_requests_completed"));
    }

    @Test
    void repeatsUntilAnImportedPullRequestBringsNothingNew() throws Exception {
        storeFeatureDone(100, "2026-09-20T08:00:00Z", listed(REPOSITORY, 1, true));
        importerStores(1, 7);

        complete(() -> storeWorkPackage(101, "Task", 100L, "", listed(REPOSITORY, 7, true)));

        verify(pullRequestImporter).importPullRequest(eq(REPOSITORY), eq(7), any());
        assertEquals(List.of(List.of(1), List.of(7)), rounds);
        assertEquals(2L, counts.get("pull_requests_completed"));
        assertEquals(1L, counts.get("features_done_in_quarter"));
    }

    @Test
    void aPullRequestThatCannotBeImportedIsCountedNamedAndNotAskedForAgain() throws Exception {
        storeFeatureDone(100, "2026-09-20T08:00:00Z", listed(REPOSITORY, 1, true), listed(REPOSITORY, 2, true));
        importerStores(2);
        doThrow(new IllegalStateException("GitHub is down"))
                .when(pullRequestImporter)
                .importPullRequest(eq(REPOSITORY), eq(1), any());

        List<String> failed = complete();

        assertEquals(List.of(REPOSITORY + "#1"), failed);
        assertEquals(1L, counts.get("pull_requests_failed"));
        assertEquals(List.of(List.of(2)), rounds);
        verify(pullRequestImporter).importPullRequest(eq(REPOSITORY), eq(1), any());
    }
}
