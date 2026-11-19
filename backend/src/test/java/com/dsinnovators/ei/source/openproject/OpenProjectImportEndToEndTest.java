package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.GitHubApiFixture;
import com.dsinnovators.ei.source.github.RecordedGitHub;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * {@code openproject-import} started over HTTP, as a person would, with OpenProject's and GitHub's
 * answers recorded. The Feature 81224 is done in 2026-Q3 and lists pull request 27100, merged before
 * the run's range and not stored: the run must pull it through the real {@code PullRequestImporter}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({OpenProjectImportEndToEndTest.RecordedOpenProject.class, RecordedGitHub.class})
class OpenProjectImportEndToEndTest extends DatabaseTest {

    private static final String REPOSITORY = "opf/openproject";
    private static final String SITE = "https://community.openproject.org";
    private static final int PULL_REQUEST_BY_NUMBER = 26626;
    private static final int PULL_REQUEST_BY_OLD_KEY = 27008;
    private static final int COMPLETION_PULL_REQUEST = 27100;
    private static final int COMPLETION_PULL_REQUEST_WITHOUT_REFERENCE = 27101;
    private static final String IMPORT = "{\"repository\":\"" + REPOSITORY
            + "\",\"mergedFrom\":\"2026-07-01\",\"mergedTo\":\"2026-07-14\",\"quarter\":\"2026-Q3\"}";

    @TestConfiguration
    static class RecordedOpenProject {

        @Bean
        OpenProjectApiFixture openProjectApiFixture() {
            return new OpenProjectApiFixture();
        }

        @Bean
        @Primary
        OpenProjectApi recordedOpenProjectApi(OpenProjectApiFixture fixture) {
            return fixture.api;
        }
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private OpenProjectApiFixture openProject;

    @Autowired
    private GitHubApiFixture github;

    @Autowired
    private RawRecordStore store;

    private JobEndpointClient jobs;

    @BeforeEach
    void startClean() {
        jobs = new JobEndpointClient(port);
        openProject.server.reset();
        github.server.reset();
        storePullRequest(
                PULL_REQUEST_BY_NUMBER, "2026-07-02T08:00:00Z", "Notify desktop users", "See " + SITE + "/wp/81224");
        storePullRequest(PULL_REQUEST_BY_OLD_KEY, "2026-07-03T08:00:00Z", "[SE-694] Fix the push", "No link here");
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

    private static String collection(String... elements) {
        return "{\"_type\":\"Collection\",\"total\":" + elements.length + ",\"count\":" + elements.length
                + ",\"_embedded\":{\"elements\":[" + String.join(",", elements) + "]}}";
    }

    private static String feature() {
        return "{\"_type\":\"WorkPackage\",\"id\":81224,\"displayId\":\"COMMS-1078\",\"subject\":\"Notify desktop users\",\"_links\":{"
                + "\"type\":{\"title\":\"Feature\"},\"project\":{\"href\":\"/api/v3/projects/1395\"},"
                + "\"ancestors\":[],\"children\":[{\"href\":\"/api/v3/work_packages/83452\"}]}}";
    }

    private static String task() {
        return "{\"_type\":\"WorkPackage\",\"id\":83452,\"displayId\":\"COMMS-1100\",\"subject\":\"Build the worker\",\"_links\":{"
                + "\"type\":{\"title\":\"Task\"},\"project\":{\"href\":\"/api/v3/projects/1395\"},"
                + "\"ancestors\":[{\"href\":\"/api/v3/work_packages/81224\"}],\"children\":[]}}";
    }

    private static String featureClosed() {
        return "{\"id\":812241,\"createdAt\":\"2026-08-10T09:00:00Z\",\"details\":[{\"html\":\"<strong>Status</strong>"
                + " changed from <i>in progress</i> to <i>closed</i>\"}],\"_links\":{\"workPackage\":"
                + "{\"href\":\"/api/v3/work_packages/81224\"},\"user\":{\"href\":\"/api/v3/users/7\"}}}";
    }

    private static String listedPullRequest(int number) {
        return "{\"id\":" + number + ",\"number\":" + number + ",\"repository\":\"" + REPOSITORY
                + "\",\"htmlUrl\":\"https://github.com/" + REPOSITORY + "/pull/" + number
                + "\",\"title\":\"Pull request " + number + "\",\"state\":\"closed\",\"draft\":false,"
                + "\"merged\":true,\"mergedAt\":\"2026-06-15T08:00:00Z\",\"createdAt\":\"2026-06-01T08:00:00Z\"}";
    }

    private void expectGet(String pathAndQuery, String answer) {
        openProject
                .server
                .expect(requestTo(URI.create(OpenProjectApiFixture.API_URL + pathAndQuery)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    /** Steps 4 to 6 for work package 81224: its tree, the details of each work package and the project. */
    private void expectTheWorkPackagesToBeStored() {
        expectGet(WorkPackageCollectionPath.withIds(List.of("81224"), 100), collection(feature()));
        expectGet(WorkPackageCollectionPath.withIds(List.of("83452"), 100), collection(task()));
        expectGet(WorkPackageCollectionPath.underAncestor(81224, 100), collection(task()));
        expectGet("/work_packages/81224/activities", collection(featureClosed()));
        expectGet(
                "/work_packages/81224/github_pull_requests",
                collection(
                        listedPullRequest(PULL_REQUEST_BY_NUMBER),
                        listedPullRequest(COMPLETION_PULL_REQUEST),
                        listedPullRequest(COMPLETION_PULL_REQUEST_WITHOUT_REFERENCE)));
        expectGet("/work_packages/83452/activities", collection());
        expectGet("/work_packages/83452/github_pull_requests", collection());
        expectGet(
                "/projects/1395",
                "{\"_type\":\"Project\",\"id\":1395,\"identifier\":\"comms\",\"name\":\"Communications\"}");
    }

    private void expectTheRangeToBeImported() {
        expectGet(WorkPackageCollectionPath.withIds(List.of("81224", "SE-694"), 100), collection(feature()));
        expectGet("/work_packages/SE-694", feature());
        expectTheWorkPackagesToBeStored();
    }

    private void expectTheCompletionPullRequestToBeImported() {
        RecordedGitHub.expectMergedPullRequest(
                github.server, COMPLETION_PULL_REQUEST, "Part of " + SITE + "/wp/81224", "2026-06-15T08:00:00Z");
        RecordedGitHub.expectMergedPullRequest(
                github.server,
                COMPLETION_PULL_REQUEST_WITHOUT_REFERENCE,
                "Nothing that names a work package",
                "2026-06-16T08:00:00Z");
    }

    private JsonNode importAndVerify() throws Exception {
        JsonNode run = jobs.awaitEnd(jobs.startAccepted("openproject-import", IMPORT));
        openProject.server.verify();
        github.server.verify();
        openProject.server.reset();
        github.server.reset();
        return run;
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private List<String> links() {
        return jdbc.query(
                "select pull_request_external_id, coalesce(work_package_id::text, '-'), reference, found_in, state "
                        + "from pull_request_work_item order by pull_request_external_id, found_in, reference",
                (row, index) -> String.join(
                        " ", row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5)));
    }

    @Test
    void aFeatureDoneInTheQuarterGetsItsMissingPullRequestThroughTheRealImporter() throws Exception {
        expectTheRangeToBeImported();
        expectTheCompletionPullRequestToBeImported();

        JsonNode run = importAndVerify();

        assertEquals("succeeded", run.get("status").stringValue(), run.toString());
        JsonNode counts = run.get("counts");
        assertEquals(1, counts.get("features_done_in_quarter").longValue());
        assertEquals(2, counts.get("pull_requests_completed").longValue());
        assertEquals(2, counts.get("pull_request.created").longValue());
        assertEquals(2, counts.get("pull_request_diff.created").longValue());
        assertEquals(2, counts.get("work_package.created").longValue());
        assertNull(counts.get("work_package.unchanged"), "a work package is stored once per run");
        assertEquals(
                2,
                count("select count(*) from raw_record where external_id = '" + REPOSITORY + "#"
                        + COMPLETION_PULL_REQUEST + "'"));
        assertEquals(
                List.of(
                        REPOSITORY + "#26626 81224 81224 openproject resolved",
                        REPOSITORY + "#26626 81224 81224 pull_request_body resolved",
                        REPOSITORY + "#27008 81224 SE-694 pull_request_title resolved",
                        REPOSITORY + "#27100 81224 81224 openproject resolved",
                        REPOSITORY + "#27100 81224 81224 pull_request_body resolved",
                        REPOSITORY + "#27101 81224 81224 openproject resolved"),
                links());
    }

    @Test
    void anOldKeyAndTheNumberLinkToTheSameWorkPackage() throws Exception {
        expectTheRangeToBeImported();
        expectTheCompletionPullRequestToBeImported();

        importAndVerify();

        assertEquals(
                List.of("81224"),
                jdbc.queryForList(
                        "select distinct work_package_id::text from pull_request_work_item"
                                + " where pull_request_external_id in ('" + REPOSITORY + "#26626', '" + REPOSITORY
                                + "#27008') and reference in ('81224', 'SE-694')",
                        String.class));
        assertEquals(
                List.of("81224", "83452"),
                jdbc.queryForList(
                        "select external_id from raw_record where record_kind = 'work_package' order by external_id",
                        String.class));
    }

    @Test
    void aSecondRunAddsNoRows() throws Exception {
        expectTheRangeToBeImported();
        expectTheCompletionPullRequestToBeImported();
        importAndVerify();
        long rawRecords = count("select count(*) from raw_record");
        List<String> linksBefore = links();
        expectTheRangeToBeImported();

        JsonNode second = importAndVerify();

        assertEquals("succeeded", second.get("status").stringValue(), second.toString());
        assertEquals(rawRecords, count("select count(*) from raw_record"));
        assertEquals(linksBefore, links());
        second.get("counts").properties().forEach(count -> {
            if (count.getKey().endsWith(".created") || count.getKey().endsWith(".new_version")) {
                throw new AssertionError("The second run wrote " + count.getKey() + " = " + count.getValue());
            }
        });
    }
}
