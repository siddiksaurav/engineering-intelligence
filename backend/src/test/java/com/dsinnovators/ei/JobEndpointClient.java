package com.dsinnovators.ei;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calls the job endpoints of a running backend, as {@code curl} would. */
public final class JobEndpointClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration LONGEST_RUN = Duration.ofSeconds(20);

    private final int port;
    private final HttpClient client = HttpClient.newHttpClient();

    public JobEndpointClient(int port) {
        this.port = port;
    }

    /** {@code POST /api/jobs/{jobName}/runs} with the body as given. */
    public HttpResponse<String> start(String jobName, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/jobs/" + jobName + "/runs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Starts a job, expects it to be accepted, and returns the run's id. */
    public long startAccepted(String jobName, String body) throws Exception {
        HttpResponse<String> response = start(jobName, body);
        if (response.statusCode() != 202) {
            throw new AssertionError("Expected 202, got " + response.statusCode() + ": " + response.body());
        }
        return json(response).get("id").longValue();
    }

    /** {@code POST} to the path with no body. */
    public HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** {@code PUT} of a JSON body to the path. */
    public HttpResponse<String> put(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Asks for the run until it is no longer running, and returns it. */
    public JsonNode awaitEnd(long runId) throws Exception {
        Instant giveUp = Instant.now().plus(LONGEST_RUN);
        while (Instant.now().isBefore(giveUp)) {
            JsonNode run = json(get("/api/jobs/runs/" + runId));
            if (!run.get("status").stringValue().equals("running")) {
                return run;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Run " + runId + " did not end within " + LONGEST_RUN);
    }

    public static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
