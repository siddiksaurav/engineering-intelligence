package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.JobEndpointClient;
import java.net.http.HttpResponse;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(JobControllerTest.TestJobs.class)
class JobControllerTest extends DatabaseTest {

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

    private JobEndpointClient jobs;

    @BeforeEach
    void holdTheSlowJob() {
        jobs = new JobEndpointClient(port);
        releaseSlowJob = new CountDownLatch(1);
    }

    @AfterEach
    void releaseTheSlowJob() {
        releaseSlowJob.countDown();
    }

    @Test
    void startingAJobAnswers202WithTheRunAndItsLocation() throws Exception {
        HttpResponse<String> response = jobs.start("quick", "{\"items\":\"3\"}");

        assertEquals(202, response.statusCode());
        JsonNode run = JobEndpointClient.json(response);
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
        long id = jobs.startAccepted("quick", "{\"items\":\"3\"}");

        JsonNode ended = jobs.awaitEnd(id);

        assertEquals("succeeded", ended.get("status").stringValue());
        assertEquals(3, ended.get("counts").get("items").longValue());
        assertTrue(ended.get("finishedAt").isString());
        assertTrue(ended.get("errorMessage").isNull());
    }

    @Test
    void anUnknownJobAnswers404WithTheKnownNames() throws Exception {
        HttpResponse<String> response = jobs.start("nope", "{}");

        assertEquals(404, response.statusCode());
        String detail = JobEndpointClient.json(response).get("detail").stringValue();
        assertTrue(detail.startsWith("No job named 'nope'. Known jobs: ["), detail);
        assertTrue(detail.contains("quick"), detail);
    }

    @Test
    void aMissingArgumentAnswers400AndNamesIt() throws Exception {
        HttpResponse<String> response = jobs.start("quick", "{}");

        assertEquals(400, response.statusCode());
        assertEquals(
                "The argument 'items' is required",
                JobEndpointClient.json(response).get("detail").stringValue());
    }

    @Test
    void anArgumentThatIsNotTextAnswers400AndNamesIt() throws Exception {
        HttpResponse<String> response = jobs.start("quick", "{\"items\":3}");

        assertEquals(400, response.statusCode());
        assertEquals(
                "The argument 'items' must be text, was 3",
                JobEndpointClient.json(response).get("detail").stringValue());
    }

    @Test
    void aBodyThatIsNotAnObjectAnswers400() throws Exception {
        HttpResponse<String> response = jobs.start("quick", "[\"items\"]");

        assertEquals(400, response.statusCode());
        assertEquals(
                "The arguments must be a JSON object of texts",
                JobEndpointClient.json(response).get("detail").stringValue());
    }

    @Test
    void startingARunningJobAnswers409WithTheRunningRun() throws Exception {
        long runningId = jobs.startAccepted("slow", "{}");

        HttpResponse<String> response = jobs.start("slow", "{}");

        assertEquals(409, response.statusCode());
        JsonNode problem = JobEndpointClient.json(response);
        assertEquals(
                "The job 'slow' is already running as run " + runningId,
                problem.get("detail").stringValue());
        assertEquals(runningId, problem.get("runningRunId").longValue());
        releaseSlowJob.countDown();
        assertEquals("succeeded", jobs.awaitEnd(runningId).get("status").stringValue());
    }

    @Test
    void anUnknownRunAnswers404() throws Exception {
        HttpResponse<String> response = jobs.get("/api/jobs/runs/999999");

        assertEquals(404, response.statusCode());
        assertEquals(
                "No job run with id 999999",
                JobEndpointClient.json(response).get("detail").stringValue());
    }
}
