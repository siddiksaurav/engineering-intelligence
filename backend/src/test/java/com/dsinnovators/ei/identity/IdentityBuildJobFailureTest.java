package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecordStore;
import com.dsinnovators.ei.source.github.GitHubRecordKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/** {@code identity-build} when the candidates of one account cannot be read: the others still get their person. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityBuildJobFailureTest extends DatabaseTest {

    private static final String HEALTHY_PULL_REQUEST = "opf/openproject#1";
    private static final String BROKEN_PULL_REQUEST = "opf/openproject#2";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RawRecordStore store;

    @MockitoSpyBean
    private OpenProjectCandidates candidates;

    private void storePullRequest(String externalId, long databaseId, String login) {
        store.store(
                "github",
                GitHubRecordKind.PULL_REQUEST.recordKind(),
                externalId,
                "https://github.com/" + externalId,
                CanonicalJson.parse("{\"author\":{\"login\":\"%s\",\"__typename\":\"User\",\"databaseId\":%d}}"
                        .formatted(login, databaseId)));
    }

    @Test
    void anAccountThatFailsIsCountedAndNamedAndTheOtherPersonsAreStillWritten() throws Exception {
        storePullRequest(HEALTHY_PULL_REQUEST, 10, "healthy");
        storePullRequest(BROKEN_PULL_REQUEST, 11, "broken");
        doThrow(new IllegalStateException("the work items of the pull request cannot be read"))
                .when(candidates)
                .of(argThat((List<String> pullRequests) -> pullRequests.contains(BROKEN_PULL_REQUEST)));
        JobEndpointClient jobs = new JobEndpointClient(port);

        JsonNode run = jobs.awaitEnd(jobs.startAccepted("identity-build", "{}"));

        assertEquals("failed", run.get("status").stringValue());
        assertTrue(run.get("errorMessage").stringValue().contains("broken"), run.toString());
        assertEquals(1, run.get("counts").get("accounts_failed").longValue());
        assertEquals(
                List.of("broken", "healthy"),
                jdbc.queryForList("select display_name from person order by display_name", String.class));
        assertEquals(1, run.get("counts").get("persons_without_proposal").longValue());
    }
}
