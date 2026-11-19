package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.PullRequestImporter;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.util.StreamUtils;

class OpenProjectImportJobTest extends DatabaseTest {

    private static final String REPOSITORY = "opf/openproject";
    private static final String SITE = "https://community.openproject.org";
    private static final String EMPTY_COLLECTION = collection();

    @Autowired
    private RawRecordStore store;

    @Autowired
    private StoredPullRequests storedPullRequests;

    @Autowired
    private OpenProjectSource source;

    @Autowired
    private WorkPackageRecorder workPackageRecorder;

    @Autowired
    private ProjectRecorder projectRecorder;

    @Autowired
    private PullRequestWorkItemLinks links;

    @Autowired
    private StoredWorkPackages storedWorkPackages;

    private final PullRequestImporter pullRequestImporter = mock(PullRequestImporter.class);

    private OpenProjectApiFixture openProject = new OpenProjectApiFixture();

    private final Map<String, Long> counts = new TreeMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);

    private OpenProjectImportJob job() {
        WorkPackageFetcher fetcher = new WorkPackageFetcher(openProject.api, openProject.properties);
        return new OpenProjectImportJob(
                storedPullRequests,
                source,
                new WorkPackageReferenceResolver(openProject.api, openProject.properties),
                new WorkPackageCollector(fetcher),
                new WorkPackageImporter(fetcher, workPackageRecorder, projectRecorder),
                links,
                new FeatureCompletion(storedWorkPackages, storedPullRequests, pullRequestImporter));
    }

    private static JobArguments range() {
        return JobArguments.of(Map.of(
                "repository", REPOSITORY, "mergedFrom", "2026-07-01", "mergedTo", "2026-07-14", "quarter", "2026-Q3"));
    }

    private static String recorded(String name) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("openproject/" + name).getInputStream(), StandardCharsets.UTF_8);
    }

    private static String collection(String... elements) {
        return "{\"_type\":\"Collection\",\"total\":" + elements.length + ",\"count\":" + elements.length
                + ",\"_embedded\":{\"elements\":[" + String.join(",", elements) + "]}}";
    }

    private static String href(long workPackage) {
        return "{\"href\":\"/api/v3/work_packages/" + workPackage + "\"}";
    }

    private static String workPackage(long id, String type, Long parent, long... children) {
        String ancestors = parent == null ? "[]" : "[" + href(parent) + "]";
        String childLinks = Arrays.stream(children)
                .mapToObj(OpenProjectImportJobTest::href)
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"_type\":\"WorkPackage\",\"id\":" + id + ",\"subject\":\"Work package " + id
                + "\",\"_links\":{\"type\":{\"title\":\"" + type
                + "\"},\"project\":{\"href\":\"/api/v3/projects/1395\"},\"parent\":"
                + (parent == null ? "{\"href\":null}" : href(parent)) + ",\"ancestors\":" + ancestors
                + ",\"children\":" + childLinks + "}}";
    }

    private void storePullRequest(int number, String mergedAt, String title, String body) {
        store.store(
                "github",
                "pull_request",
                REPOSITORY + "#" + number,
                "https://github.com/" + REPOSITORY + "/pull/" + number,
                CanonicalJson.parse("{\"number\":" + number + ",\"title\":\"" + title + "\",\"body\":\"" + body
                        + "\",\"mergedAt\":\"" + mergedAt + "\"}"));
    }

    private void storeThePullRequests() {
        storePullRequest(26626, "2026-07-02T08:00:00Z", "Notify desktop users", SITE + "/wp/81224");
        storePullRequest(27008, "2026-07-03T08:00:00Z", "[SE-694] Fix the push", SITE + "/wp/HIDDEN-1");
        storePullRequest(27009, "2026-07-04T08:00:00Z", "A change without a work item", "No link here");
    }

    private void expectGet(String pathAndQuery, DefaultResponseCreator answer) {
        openProject
                .server
                .expect(requestTo(URI.create(OpenProjectApiFixture.API_URL + pathAndQuery)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(answer);
    }

    private static DefaultResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static DefaultResponseCreator notFound() throws IOException {
        return withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(recorded("work-package-not-found.json"));
    }

    private void expectTheReferencesToResolve() throws IOException {
        expectGet(
                WorkPackageCollectionPath.withIds(List.of("81224", "HIDDEN-1", "SE-694"), 100),
                withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(recorded("work-packages-bad-filter.json")));
        expectGet("/work_packages/81224", json(workPackage(81224, "Feature", null, 83452)));
        expectGet("/work_packages/HIDDEN-1", notFound());
        expectGet("/work_packages/SE-694", json(workPackage(81224, "Feature", null, 83452)));
    }

    private void expectTheWorkPackagesToBeCollected() {
        expectGet(
                WorkPackageCollectionPath.withIds(List.of("81224"), 100),
                json(collection(workPackage(81224, "Feature", null, 83452))));
        expectGet(
                WorkPackageCollectionPath.withIds(List.of("83452"), 100),
                json(collection(workPackage(83452, "Task", 81224L, 90001))));
        expectGet(
                WorkPackageCollectionPath.underAncestor(81224, 100),
                json(collection(workPackage(83452, "Task", 81224L, 90001), workPackage(90001, "Task", 83452L))));
    }

    private void expectDetailsOfTheFeature() throws IOException {
        expectGet("/work_packages/81224/activities", json(recorded("activities-81224.json")));
        expectGet("/work_packages/81224/github_pull_requests", json(recorded("github-pull-requests-81224.json")));
    }

    private void expectEmptyDetails(long workPackage) {
        expectGet("/work_packages/" + workPackage + "/activities", json(EMPTY_COLLECTION));
        expectGet("/work_packages/" + workPackage + "/github_pull_requests", json(EMPTY_COLLECTION));
    }

    private void expectTheProject() throws IOException {
        expectGet("/projects/1395", json(recorded("project-1395.json")));
    }

    private void expectAWholeRun() throws IOException {
        expectTheReferencesToResolve();
        expectTheWorkPackagesToBeCollected();
        expectDetailsOfTheFeature();
        expectEmptyDetails(83452);
        expectEmptyDetails(90001);
        expectTheProject();
    }

    private List<String> linkRows() {
        return jdbc.query(
                "select pull_request_external_id, coalesce(work_package_id::text, '-'), reference, found_in, state "
                        + "from pull_request_work_item order by pull_request_external_id, found_in, reference",
                (row, index) -> String.join(
                        " ", row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5)));
    }

    private int rows(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void theJobIsCalledOpenprojectImport() {
        assertEquals("openproject-import", job().name());
    }

    @Test
    void linksEachPullRequestAndStoresTheWorkPackagesBehindIt() throws Exception {
        storeThePullRequests();
        expectAWholeRun();

        job().run(range(), jobCounts);

        openProject.server.verify();
        assertEquals(
                List.of(
                        REPOSITORY + "#26626 81224 81224 openproject resolved",
                        REPOSITORY + "#26626 81224 81224 pull_request_body resolved",
                        REPOSITORY + "#27008 81224 81224 openproject resolved",
                        REPOSITORY + "#27008 - HIDDEN-1 pull_request_body not_visible",
                        REPOSITORY + "#27008 81224 SE-694 pull_request_title resolved"),
                linkRows());
        assertEquals(
                List.of("81224", "83452", "90001"),
                jdbc.queryForList(
                        "select external_id from raw_record where record_kind = 'work_package' order by external_id",
                        String.class));
        assertEquals(
                List.of("1395"),
                jdbc.queryForList("select external_id from project order by external_id", String.class));
        assertEquals(
                Map.ofEntries(
                        Map.entry("pull_requests_read", 3L),
                        Map.entry("references_found", 3L),
                        Map.entry("references_resolved", 2L),
                        Map.entry("references_not_visible", 1L),
                        Map.entry("work_package.created", 3L),
                        Map.entry("work_package_activity.created", 48L),
                        Map.entry("work_package_pull_requests.created", 3L),
                        Map.entry("project.created", 1L),
                        Map.entry("pull_requests_linked", 2L)),
                new TreeMap<>(counts));
    }

    @Test
    void aSecondRunAddsNoRowsAndCountsEverythingUnchanged() throws Exception {
        storeThePullRequests();
        expectAWholeRun();
        job().run(range(), jobCounts);
        int rawRecords = rows("raw_record");
        List<String> linksBefore = linkRows();
        openProject = new OpenProjectApiFixture();
        expectAWholeRun();
        counts.clear();

        job().run(range(), jobCounts);

        assertEquals(rawRecords, rows("raw_record"));
        assertEquals(linksBefore, linkRows());
        assertEquals(3L, counts.get("work_package.unchanged"));
        assertEquals(48L, counts.get("work_package_activity.unchanged"));
        assertEquals(1L, counts.get("project.unchanged"));
        assertEquals(
                List.of(),
                counts.keySet().stream()
                        .filter(name -> name.endsWith(".created"))
                        .toList());
    }

    @Test
    void aWorkPackageThatFailsIsCountedTheOthersAreStoredAndTheRunFailsNamingIt() throws Exception {
        storeThePullRequests();
        expectTheReferencesToResolve();
        expectTheWorkPackagesToBeCollected();
        expectDetailsOfTheFeature();
        expectEmptyDetails(83452);
        openProject
                .server
                .expect(
                        times(4),
                        requestTo(URI.create(OpenProjectApiFixture.API_URL + "/work_packages/90001/activities")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        expectTheProject();

        OpenProjectImportFailedException failed =
                assertThrows(OpenProjectImportFailedException.class, () -> job().run(range(), jobCounts));

        assertEquals("1 of 3 work packages failed: #90001", failed.getMessage());
        assertEquals(1L, counts.get("work_packages_failed"));
        assertEquals(2, rows("raw_record where record_kind = 'work_package'"));
        assertEquals(5, linkRows().size());
    }

    @Test
    void aListingThatFailsFailsTheRunBeforeAnythingIsStoredOrLinked() {
        storeThePullRequests();
        openProject
                .server
                .expect(
                        times(4),
                        requestTo(URI.create(OpenProjectApiFixture.API_URL
                                + WorkPackageCollectionPath.withIds(List.of("81224", "HIDDEN-1", "SE-694"), 100))))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(OpenProjectApiException.class, () -> job().run(range(), jobCounts));

        assertEquals(0, rows("pull_request_work_item"));
        assertEquals(0, rows("raw_record where record_kind = 'work_package'"));
    }

    @Test
    void aNumberThatIsNotStoredIsRefusedBeforeARunIsRecorded() {
        storeThePullRequests();

        IllegalArgumentException refused = assertThrows(
                IllegalArgumentException.class,
                () -> job().checkArguments(JobArguments.of(
                        Map.of("repository", REPOSITORY, "pullRequestNumbers", "26626,5", "quarter", "2026-Q3"))));

        assertEquals("Pull request opf/openproject#5 is not stored; run github-import first", refused.getMessage());
    }

    @Test
    void pullRequestsGivenByNumberAreReadLikeThoseOfARange() throws Exception {
        storePullRequest(27009, "2026-07-04T08:00:00Z", "A change without a work item", "No link here");

        job().run(
                        JobArguments.of(
                                Map.of("repository", REPOSITORY, "pullRequestNumbers", "27009", "quarter", "2026-Q3")),
                        jobCounts);

        assertEquals(1L, counts.get("pull_requests_read"));
        assertEquals(0L, counts.get("references_found"));
    }
}
