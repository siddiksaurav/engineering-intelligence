package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Sleeper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Reads OpenProject's public API v3 without login, one call at a time with a pause between calls.
 * A call that failed for a passing reason (a server error, a timeout, {@code 429}) is tried again
 * with growing waits. Every answer is parsed with {@link CanonicalJson#parse(String)} and must be a
 * JSON object.
 */
class OpenProjectApi {

    private static final Logger LOG = LoggerFactory.getLogger(OpenProjectApi.class);

    private final RestClient client;
    private final OpenProjectProperties properties;
    private final Sleeper sleeper;
    private boolean calledBefore;

    OpenProjectApi(RestClient client, OpenProjectProperties properties, Sleeper sleeper) {
        this.client = client;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * Reads one answer. Synchronized so that two callers never make calls at the same time.
     *
     * @param pathAndQuery starts with a slash, for example {@code /work_packages/81224}
     * @throws OpenProjectNotFoundException on {@code 404}
     * @throws OpenProjectBadRequestException on {@code 400}
     * @throws OpenProjectApiException when the call fails after its retries, or the answer is not a JSON object
     */
    synchronized JsonNode get(String pathAndQuery) throws InterruptedException {
        if (!pathAndQuery.startsWith("/")) {
            throw new IllegalArgumentException("An OpenProject path starts with a slash, was " + pathAndQuery);
        }
        pauseAfterThePreviousCall();
        URI uri = URI.create(root() + pathAndQuery);
        String call = "GET " + pathAndQuery;
        for (int attempt = 1; ; attempt++) {
            Answer answer;
            try {
                answer = exchange(uri);
            } catch (ResourceAccessException unreachable) {
                if (attempt > properties.retries()) {
                    throw new OpenProjectApiException(
                            "OpenProject could not be reached for " + call + ", " + attempt + " attempts", unreachable);
                }
                waitBeforeRetry(call, attempt, Duration.ZERO, unreachable.getMessage());
                continue;
            }
            int status = answer.status();
            if (status >= 200 && status < 300) {
                return parse(answer.body(), call);
            }
            if (status == 404) {
                throw new OpenProjectNotFoundException("OpenProject has nothing at " + call);
            }
            if (status == 400) {
                throw new OpenProjectBadRequestException("OpenProject did not accept " + call);
            }
            if (status == 429 || status >= 500) {
                if (attempt > properties.retries()) {
                    throw new OpenProjectApiException(
                            status == 429
                                    ? "OpenProject kept limiting the rate of " + call + ", " + attempt + " attempts"
                                    : "OpenProject answered " + status + " to " + call + ", " + attempt + " attempts");
                }
                waitBeforeRetry(call, attempt, retryAfter(answer.headers()), "status " + status);
                continue;
            }
            throw new OpenProjectApiException("OpenProject answered " + status + " to " + call);
        }
    }

    private void pauseAfterThePreviousCall() throws InterruptedException {
        if (calledBefore && properties.pause().isPositive()) {
            sleeper.sleep(properties.pause());
        }
        calledBefore = true;
    }

    private Answer exchange(URI uri) {
        return client.method(HttpMethod.GET)
                .uri(uri)
                .exchange((sent, response) -> new Answer(
                        response.getStatusCode().value(),
                        response.getHeaders(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private static JsonNode parse(String body, String call) {
        JsonNode parsed;
        try {
            parsed = CanonicalJson.parse(body);
        } catch (JacksonException notJson) {
            throw new OpenProjectApiException("OpenProject answered " + call + " with text that is not JSON", notJson);
        }
        if (!parsed.isObject()) {
            throw new OpenProjectApiException(
                    "OpenProject answered " + call + " with a value that is not a JSON object");
        }
        return parsed;
    }

    /** The server's own wait wins over the growing one; zero when it named none. */
    private static Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Duration.ZERO;
        }
        OptionalLong seconds = parseSeconds(value);
        return seconds.isPresent() ? Duration.ofSeconds(seconds.getAsLong()) : Duration.ZERO;
    }

    // Retry-After may also be a date; the growing wait covers that rare form.
    private static OptionalLong parseSeconds(String value) {
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException notSeconds) {
            return OptionalLong.empty();
        }
    }

    private void waitBeforeRetry(String call, int attempt, Duration serverWait, String reason)
            throws InterruptedException {
        Duration wait = serverWait.isZero() ? properties.retryWait().multipliedBy(attempt) : serverWait;
        LOG.warn("{} failed ({}); try {} of {} in {}", call, reason, attempt + 1, properties.retries() + 1, wait);
        sleeper.sleep(wait);
    }

    private String root() {
        String root = properties.apiUrl().toString();
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }

    private record Answer(int status, HttpHeaders headers, String body) {}
}
