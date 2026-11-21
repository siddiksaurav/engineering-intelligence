package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.GitHubRecordKind;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

/** {@code identity-build} started over HTTP, as a person would, on stored GitHub and OpenProject records. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityBuildJobTest extends DatabaseTest {

    private static final String AUTHOR_PULL_REQUEST = "opf/openproject#1";
    private static final long KABIRU = 80250L;
    private static final long BRUNO = 99165L;
    private static final String NO_ARGUMENTS = "{}";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RawRecordStore store;

    private JobEndpointClient jobs;

    @BeforeEach
    void storeRecords() {
        jobs = new JobEndpointClient(port);
        storeGitHub(GitHubRecordKind.PULL_REQUEST, AUTHOR_PULL_REQUEST, 10, "author", "User");
        storeGitHub(GitHubRecordKind.REVIEW, AUTHOR_PULL_REQUEST + "/review/1", 12, "reviewer-only", "User");
        storeGitHub(GitHubRecordKind.PULL_REQUEST, "opf/openproject#2", 11, "dependabot", "User");
        storeGitHub(GitHubRecordKind.REVIEW, AUTHOR_PULL_REQUEST + "/review/2", 13, "openprojectci", "Bot");
        storeWorkItem(100, "[" + user(KABIRU, "Kabiru Mwenja") + "," + user(BRUNO, "Bruno Pagno") + "]");
        storeWorkItem(101, "[" + user(KABIRU, "Kabiru Mwenja") + "]");
        link(AUTHOR_PULL_REQUEST, 100);
        link(AUTHOR_PULL_REQUEST, 101);
    }

    private void storeGitHub(GitHubRecordKind kind, String externalId, long databaseId, String login, String type) {
        store.store(
                "github",
                kind.recordKind(),
                externalId,
                "https://github.com/" + externalId,
                CanonicalJson.parse("{\"author\":{\"login\":\"%s\",\"__typename\":\"%s\",\"databaseId\":%d}}"
                        .formatted(login, type, databaseId)));
    }

    private static String user(long id, String name) {
        return "{\"title\":\"%s\",\"href\":\"/api/v3/users/%d\"}".formatted(name, id);
    }

    private void storeWorkItem(long number, String developers) {
        store.store(
                "openproject",
                "work_package",
                Long.toString(number),
                "https://community.openproject.org/api/v3/work_packages/" + number,
                CanonicalJson.parse("{\"id\":%d,\"_links\":{\"customField270\":%s}}".formatted(number, developers)));
    }

    private void link(String pullRequest, long workPackage) {
        jdbc.update("""
                insert into pull_request_work_item (pull_request_external_id, work_package_id, reference, found_in, state)
                values (?, ?, ?, 'openproject', 'resolved')
                """, pullRequest, workPackage, Long.toString(workPackage));
    }

    private JsonNode runJob() throws Exception {
        return jobs.awaitEnd(jobs.startAccepted("identity-build", NO_ARGUMENTS));
    }

    private List<String> humanLogins() {
        return jdbc.queryForList("select display_name from person order by display_name", String.class);
    }

    private List<Map<String, Object>> proposals() {
        return jdbc.queryForList("select p.display_name, o.openproject_user_id, o.state from identity_proposal o"
                + " join person p on p.id = o.person_id order by o.id");
    }

    @Test
    void twoRunsGiveOnePersonPerHumanAccountAndNoBotAndKeepARejectedPairOut() throws Exception {
        JsonNode first = runJob();

        assertEquals("succeeded", first.get("status").stringValue());
        assertEquals(List.of("author", "reviewer-only"), humanLogins());
        assertEquals(
                List.of(Map.of("display_name", "author", "openproject_user_id", KABIRU, "state", "proposed")),
                proposals());
        JsonNode counts = first.get("counts");
        assertEquals(4, counts.get("accounts_read").longValue());
        assertEquals(2, counts.get("bots_skipped").longValue());
        assertEquals(2, counts.get("persons_created").longValue());
        assertEquals(1, counts.get("proposals_written").longValue());
        assertEquals(1, counts.get("persons_without_proposal").longValue());

        jdbc.execute("select reject_identity_proposal(id) from identity_proposal");
        JsonNode second = runJob();

        assertEquals("succeeded", second.get("status").stringValue());
        assertEquals(List.of("author", "reviewer-only"), humanLogins());
        assertEquals(
                List.of(
                        Map.of("display_name", "author", "openproject_user_id", KABIRU, "state", "rejected"),
                        Map.of("display_name", "author", "openproject_user_id", BRUNO, "state", "proposed")),
                proposals());
        assertEquals(0, second.get("counts").path("persons_created").asLong(0L));
        assertEquals(1, second.get("counts").get("proposals_written").longValue());
    }

    @Test
    void aThirdRunChangesNothing() throws Exception {
        runJob();

        JsonNode counts = runJob().get("counts");

        assertEquals(1, counts.get("proposals_unchanged").longValue());
        assertEquals(0, counts.path("proposals_written").asLong(0L));
        assertEquals(0, counts.path("persons_created").asLong(0L));
    }

    @Test
    void aRenamedLoginIsCountedAndKeepsThePerson() throws Exception {
        runJob();
        storeGitHub(GitHubRecordKind.PULL_REQUEST, AUTHOR_PULL_REQUEST, 10, "author-renamed", "User");

        JsonNode counts = runJob().get("counts");

        assertEquals(1, counts.get("logins_updated").longValue());
        assertEquals(List.of("author-renamed", "reviewer-only"), humanLogins());
    }

    @Test
    void anArgumentIsRefused() throws Exception {
        HttpResponse<String> response = jobs.start("identity-build", "{\"quarter\":\"2026-Q3\"}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("quarter"), response.body());
    }
}
