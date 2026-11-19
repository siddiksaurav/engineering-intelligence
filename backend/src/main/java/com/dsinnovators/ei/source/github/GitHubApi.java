package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.common.CanonicalJson;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls GitHub's GraphQL and REST APIs with the read-only token, one call at a time. It keeps
 * inside the hourly budget, waits when GitHub limits the rate, and tries a call again when it
 * failed for a passing reason. Every answer is parsed with {@link CanonicalJson#parse(String)}.
 */
class GitHubApi {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubApi.class);

    // GitHub's advice when it limits the rate without saying for how long.
    private static final Duration RATE_LIMIT_WAIT_WITHOUT_TIME = Duration.ofMinutes(1);
    private static final Duration AFTER_RESET = Duration.ofSeconds(1);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern NEXT_PAGE = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    private final RestClient client;
    private final GitHubProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;

    GitHubApi(RestClient client, GitHubProperties properties, Clock clock, Sleeper sleeper) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Runs a GraphQL query and returns its {@code data}.
     *
     * @throws GitHubNotFoundException when every GraphQL error says that something was not found
     * @throws GitHubApiException for any other GraphQL error, or when the call fails
     */
    JsonNode graphQl(String query, Map<String, ?> variables) throws InterruptedException {
        ObjectNode request = JSON.createObjectNode();
        request.put("query", query);
        request.set("variables", JSON.valueToTree(variables));
        Answer answer = send(HttpMethod.POST, URI.create(root() + "/graphql"), JSON.writeValueAsString(request));
        JsonNode body = CanonicalJson.parse(answer.body());
        JsonNode errors = body.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            throw graphQlFailure(errors);
        }
        JsonNode data = body.path("data");
        if (!data.isObject()) {
            throw new GitHubApiException("GitHub answered a GraphQL query without data");
        }
        return data;
    }

    /**
     * Reads a REST list and every page after it, following the {@code Link} header.
     *
     * @param pathAndQuery for example {@code /repos/opf/openproject/pulls/1/files?per_page=100}
     * @return the elements of all pages, in order
     */
    List<JsonNode> getAllPages(String pathAndQuery) throws InterruptedException {
        List<JsonNode> elements = new ArrayList<>();
        Optional<URI> page = Optional.of(URI.create(root() + pathAndQuery));
        while (page.isPresent()) {
            Answer answer = send(HttpMethod.GET, page.get(), null);
            JsonNode list = CanonicalJson.parse(answer.body());
            if (!list.isArray()) {
                throw new GitHubApiException("GitHub answered GET " + pathOf(page.get()) + " without a list");
            }
            list.forEach(elements::add);
            page = nextPage(answer.headers());
        }
        return elements;
    }

    private Answer send(HttpMethod method, URI uri, String body) throws InterruptedException {
        if (!properties.hasToken()) {
            throw new GitHubTokenMissingException();
        }
        String call = method.name() + " " + pathOf(uri);
        for (int attempt = 1; ; attempt++) {
            Answer answer;
            try {
                answer = exchange(method, uri, body);
            } catch (ResourceAccessException unreachable) {
                if (attempt > properties.retries()) {
                    throw new GitHubApiException(
                            "GitHub could not be reached for " + call + ", " + attempt + " attempts", unreachable);
                }
                waitBeforeRetry(call, attempt, unreachable.getMessage());
                continue;
            }
            int status = answer.status();
            if (status >= 200 && status < 300) {
                waitWhenTheBudgetIsLow(answer.headers());
                return answer;
            }
            if (status == 401) {
                throw tokenRefused(status, call);
            }
            if (isRateLimited(answer)) {
                if (attempt > properties.retries()) {
                    throw new GitHubApiException(
                            "GitHub kept limiting the rate of " + call + ", " + attempt + " attempts");
                }
                Duration wait = rateLimitWait(answer.headers());
                LOG.info("GitHub limits the rate of {}; waiting {}", call, wait);
                sleeper.sleep(wait);
                continue;
            }
            if (status == 403) {
                throw tokenRefused(status, call);
            }
            if (status == 404) {
                throw new GitHubNotFoundException("GitHub has nothing at " + call);
            }
            if (status >= 500) {
                if (attempt > properties.retries()) {
                    throw new GitHubApiException(
                            "GitHub answered " + status + " to " + call + ", " + attempt + " attempts");
                }
                waitBeforeRetry(call, attempt, "status " + status);
                continue;
            }
            throw new GitHubApiException("GitHub answered " + status + " to " + call);
        }
    }

    private Answer exchange(HttpMethod method, URI uri, String body) {
        RestClient.RequestBodySpec request =
                client.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((sent, response) -> new Answer(
                response.getStatusCode().value(),
                response.getHeaders(),
                readBody(response.getBody().readAllBytes())));
    }

    private static String readBody(byte[] bytes) throws IOException {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void waitBeforeRetry(String call, int attempt, String reason) throws InterruptedException {
        Duration wait = properties.retryWait().multipliedBy(attempt);
        LOG.warn("{} failed ({}); try {} of {} in {}", call, reason, attempt + 1, properties.retries() + 1, wait);
        sleeper.sleep(wait);
    }

    private void waitWhenTheBudgetIsLow(HttpHeaders headers) throws InterruptedException {
        Optional<Integer> remaining = intHeader(headers, "x-ratelimit-remaining");
        Optional<Instant> reset = resetTime(headers);
        if (remaining.isPresent() && reset.isPresent() && remaining.get() <= properties.rateLimitReserve()) {
            Duration wait = Duration.between(clock.instant(), reset.get()).plus(AFTER_RESET);
            if (wait.isPositive()) {
                LOG.info("GitHub budget down to {} calls; waiting {} until the reset", remaining.get(), wait);
                sleeper.sleep(wait);
            }
        }
    }

    private static boolean isRateLimited(Answer answer) {
        if (answer.status() == 429) {
            return true;
        }
        return answer.status() == 403
                && (answer.headers().containsHeader("retry-after")
                        || intHeader(answer.headers(), "x-ratelimit-remaining").orElse(1) == 0
                        || answer.body().toLowerCase(Locale.ROOT).contains("rate limit"));
    }

    private Duration rateLimitWait(HttpHeaders headers) {
        Optional<Integer> retryAfter = intHeader(headers, "retry-after");
        if (retryAfter.isPresent()) {
            return Duration.ofSeconds(retryAfter.get());
        }
        Optional<Instant> reset = resetTime(headers);
        if (intHeader(headers, "x-ratelimit-remaining").orElse(1) == 0 && reset.isPresent()) {
            Duration untilReset = Duration.between(clock.instant(), reset.get()).plus(AFTER_RESET);
            return untilReset.isPositive() ? untilReset : AFTER_RESET;
        }
        return RATE_LIMIT_WAIT_WITHOUT_TIME;
    }

    private Optional<URI> nextPage(HttpHeaders headers) {
        String link = headers.getFirst(HttpHeaders.LINK);
        if (link == null) {
            return Optional.empty();
        }
        Matcher next = NEXT_PAGE.matcher(link);
        if (!next.find()) {
            return Optional.empty();
        }
        String url = next.group(1);
        if (!url.startsWith(root() + "/")) {
            throw new GitHubApiException("GitHub sent a next page outside " + root() + ": " + url);
        }
        return Optional.of(URI.create(url));
    }

    private static GitHubApiException graphQlFailure(JsonNode errors) {
        List<JsonNode> list = StreamSupport.stream(errors.spliterator(), false).toList();
        String messages = String.join(
                "; ",
                list.stream().map(error -> error.path("message").asString()).toList());
        boolean allNotFound = list.stream()
                .allMatch(error -> "NOT_FOUND".equals(error.path("type").asString()));
        return allNotFound
                ? new GitHubNotFoundException(messages)
                : new GitHubApiException("GitHub GraphQL errors: " + messages);
    }

    private static GitHubTokenRefusedException tokenRefused(int status, String call) {
        return new GitHubTokenRefusedException(
                "GitHub refused the token (" + status + ") for " + call + ". Check GITHUB_TOKEN");
    }

    private static Optional<Instant> resetTime(HttpHeaders headers) {
        return longHeader(headers, "x-ratelimit-reset").map(Instant::ofEpochSecond);
    }

    private static Optional<Integer> intHeader(HttpHeaders headers, String name) {
        return longHeader(headers, name).map(Math::toIntExact);
    }

    private static Optional<Long> longHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException notANumber) {
            throw new GitHubApiException(
                    "GitHub sent the header " + name + " with a value that is not a number: " + value, notANumber);
        }
    }

    private String root() {
        String root = properties.apiUrl().toString();
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }

    private static String pathOf(URI uri) {
        return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
    }

    private record Answer(int status, HttpHeaders headers, String body) {}
}
