package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(JobControllerTest.TestJobs.class)
class JobControllerTest extends DatabaseTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    // A new latch per test: a released latch would let the slow job end at once.
    private static volatile CountDownLatch releaseSlowJob = new CountDownLatch(1);

    @TestConfiguration
    static class TestJobs {

        /** Counts its argument; refuses a run without one. */
        @Bean
        Job quickJob() {
            return new Job() {
                @Override
                public String name() {
                    return "quick";
                }

                @Override
                public void checkArguments(JobArguments arguments) {
                    arguments.require("items");
                }

                @Override
                public void run(JobArguments arguments, JobCounts counts) {
                    counts.add("items", Long.parseLong(arguments.require("items")));
                }
            };
        }

        /** Runs until the test releases it. */
        @Bean
        Job slowJob() {
            return new Job() {
                @Override
                public String name() {
                    return "slow";
                }

                @Override
                public void checkArguments(JobArguments arguments) {}

                @Override
                public void run(JobArguments arguments, JobCounts counts) throws InterruptedException {
                    if (!releaseSlowJob.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The test never released the slow job");
                    }
                }
            };
        }
    }

    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void holdTheSlowJob() {
        releaseSlowJob = new CountDownLatch(1);
    }

    @AfterEach
    void releaseTheSlowJob() {
        releaseSlowJob.countDown();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode awaitEnd(long runId) throws Exception {
        Instant giveUp = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(giveUp)) {
            JsonNode run = JSON.readTree(get("/api/jobs/runs/" + runId).body());
            if (!run.get("status").stringValue().equals("running")) {
                return run;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Run " + runId + " did not end within 10 seconds");
    }

    @Test
    void startingAJobAnswers202WithTheRunAndItsLocation() throws Exception {
        HttpResponse<String> response = post("/api/jobs/quick/runs", "{\"items\":\"3\"}");

        assertEquals(202, response.statusCode());
        JsonNode run = JSON.readTree(response.body());
        long id = run.get("id").longValue();
        assertEquals("quick", run.get("jobName").stringValue());
        assertEquals("running", run.get("status").stringValue());
        assertEquals("api", run.get("startedBy").stringValue());
        assertEquals("3", run.get("arguments").get("items").stringValue());
        assertEquals(
                "http://localhost:" + port + "/api/jobs/runs/" + id,
                response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void theRunRunsInTheBackgroundAndEndsWithItsCounts() throws Exception {
        long id = JSON.readTree(
                        post("/api/jobs/quick/runs", "{\"items\":\"3\"}").body())
                .get("id")
                .longValue();

        JsonNode ended = awaitEnd(id);

        assertEquals("succeeded", ended.get("status").stringValue());
        assertEquals(3, ended.get("counts").get("items").longValue());
        assertTrue(ended.get("finishedAt").isString());
        assertTrue(ended.get("errorMessage").isNull());
    }

    @Test
    void anUnknownJobAnswers404WithTheKnownNames() throws Exception {
        HttpResponse<String> response = post("/api/jobs/nope/runs", "{}");

        assertEquals(404, response.statusCode());
        String detail = JSON.readTree(response.body()).get("detail").stringValue();
        assertTrue(detail.startsWith("No job named 'nope'. Known jobs: ["), detail);
        assertTrue(detail.contains("quick"), detail);
    }

    @Test
    void aMissingArgumentAnswers400AndNamesIt() throws Exception {
        HttpResponse<String> response = post("/api/jobs/quick/runs", "{}");

        assertEquals(400, response.statusCode());
        assertEquals(
                "The argument 'items' is required",
                JSON.readTree(response.body()).get("detail").stringValue());
    }

    @Test
    void anArgumentThatIsNotTextAnswers400AndNamesIt() throws Exception {
        HttpResponse<String> response = post("/api/jobs/quick/runs", "{\"items\":3}");

        assertEquals(400, response.statusCode());
        assertEquals(
                "The argument 'items' must be text, was 3",
                JSON.readTree(response.body()).get("detail").stringValue());
    }

    @Test
    void aBodyThatIsNotAnObjectAnswers400() throws Exception {
        HttpResponse<String> response = post("/api/jobs/quick/runs", "[\"items\"]");

        assertEquals(400, response.statusCode());
        assertEquals(
                "The arguments must be a JSON object of texts",
                JSON.readTree(response.body()).get("detail").stringValue());
    }

    @Test
    void startingARunningJobAnswers409WithTheRunningRun() throws Exception {
        long runningId = JSON.readTree(post("/api/jobs/slow/runs", "{}").body())
                .get("id")
                .longValue();

        HttpResponse<String> response = post("/api/jobs/slow/runs", "{}");

        assertEquals(409, response.statusCode());
        JsonNode problem = JSON.readTree(response.body());
        assertEquals(
                "The job 'slow' is already running as run " + runningId,
                problem.get("detail").stringValue());
        assertEquals(runningId, problem.get("runningRunId").longValue());
        releaseSlowJob.countDown();
        assertEquals("succeeded", awaitEnd(runningId).get("status").stringValue());
    }

    @Test
    void anUnknownRunAnswers404() throws Exception {
        HttpResponse<String> response = get("/api/jobs/runs/999999");

        assertEquals(404, response.statusCode());
        assertEquals(
                "No job run with id 999999",
                JSON.readTree(response.body()).get("detail").stringValue());
    }
}
