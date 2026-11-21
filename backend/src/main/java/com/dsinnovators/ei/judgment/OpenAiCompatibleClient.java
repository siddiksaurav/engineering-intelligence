package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Sleeper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls the chat completions API of one OpenAI-compatible provider, one call at a time, and asks for
 * an answer that follows a strict JSON schema. A short rate limit is waited out; a long one means the
 * provider has reached its limit and is reported as {@link ProviderLimitReachedException}. A call
 * that failed for a passing reason is tried again. The key is sent in the header and appears in no
 * message and no log line. After each answer it reads the token window the provider reports and,
 * when too few tokens are left for another request, waits for the reset before the next call.
 */
final class OpenAiCompatibleClient implements JudgmentModel {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    // Groq writes a reset as "7.66s", "2m59.56s", "1h2m3s" or "50ms".
    private static final Pattern RESET_DURATION =
            Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:(\\d+(?:\\.\\d+)?)s)?(?:(\\d+)ms)?");

    private final RestClient client;
    private final JudgmentProvider provider;
    private final CallPolicy policy;
    private final Sleeper sleeper;
    private final Clock clock;

    // What the provider said about its token window in the last answer; calls are made one at a time.
    private TokenWindow tokenWindow;

    OpenAiCompatibleClient(
            RestClient client, JudgmentProvider provider, CallPolicy policy, Sleeper sleeper, Clock clock) {
        this.client = client;
        this.provider = provider;
        this.policy = policy;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    @Override
    public ModelAnswer judge(ModelRequest request) {
        String body = requestBody(request);
        waitForTokenWindow();
        for (int attempt = 1; ; attempt++) {
            Reply reply;
            try {
                reply = exchange(body);
            } catch (ResourceAccessException unreachable) {
                if (attempt > policy.retries()) {
                    throw new ModelCallException(
                            "Provider " + provider.name() + " could not be reached, " + attempt + " attempts",
                            unreachable);
                }
                waitBeforeRetry(attempt, unreachable.getMessage());
                continue;
            }
            int status = reply.status();
            if (status >= 200 && status < 300) {
                tokenWindow = tokenWindowOf(reply.headers());
                return answer(reply.body());
            }
            if (status == 429) {
                waitOutRateLimit(reply.headers(), attempt);
                continue;
            }
            if (status >= 500 && attempt <= policy.retries()) {
                waitBeforeRetry(attempt, "status " + status);
                continue;
            }
            throw new ModelCallException(
                    "Provider " + provider.name() + " answered " + status + abbreviated(reply.body()));
        }
    }

    private String requestBody(ModelRequest request) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", provider.model());
        if (provider.hasReasoningEffort()) {
            body.put("reasoning_effort", provider.reasoningEffort());
        }
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.instructions());
        messages.addObject().put("role", "user").put("content", request.input());
        ObjectNode schema =
                body.putObject("response_format").put("type", "json_schema").putObject("json_schema");
        schema.put("name", request.schemaName()).put("strict", true).set("schema", request.jsonSchema());
        return JSON.writeValueAsString(body);
    }

    private Reply exchange(String body) {
        return client.method(HttpMethod.POST)
                .uri(chatCompletionsUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((sent, response) -> new Reply(
                        response.getStatusCode().value(),
                        response.getHeaders(),
                        new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private ModelAnswer answer(String body) {
        JsonNode completion = parse(body, "its reply");
        JsonNode choice = completion.path("choices").path(0);
        if ("length".equals(choice.path("finish_reason").asString())) {
            throw new ModelCallException("Provider " + provider.name() + " cut off the answer at the token limit");
        }
        JsonNode content = choice.path("message").path("content");
        if (!content.isString()) {
            throw new ModelCallException("Provider " + provider.name() + " answered without a message");
        }
        JsonNode usage = completion.path("usage");
        return new ModelAnswer(
                parse(content.stringValue(), "the answer"),
                completion.path("model").asString(provider.model()),
                usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0));
    }

    private JsonNode parse(String text, String what) {
        try {
            return CanonicalJson.parse(text);
        } catch (JacksonException notJson) {
            throw new ModelCallException(
                    "Provider " + provider.name() + " sent " + what + " that is not JSON", notJson);
        }
    }

    /** Waits for the reset when the provider reported too few tokens left for one more request. */
    private void waitForTokenWindow() {
        TokenWindow window = tokenWindow;
        tokenWindow = null;
        if (window == null || window.remainingTokens() >= policy.minRemainingTokens()) {
            return;
        }
        Duration wait = Duration.between(clock.instant(), window.resetsAt());
        if (wait.isNegative() || wait.isZero()) {
            return;
        }
        if (wait.compareTo(policy.maxRateLimitWait()) > 0) {
            throw ProviderLimitReachedException.waitTooLong(provider.name(), wait, policy.maxRateLimitWait());
        }
        LOG.info(
                "Provider {} has {} tokens left; waiting {} for the window",
                provider.name(),
                window.remainingTokens(),
                wait);
        sleep(wait);
    }

    private TokenWindow tokenWindowOf(HttpHeaders headers) {
        String remaining = headers.getFirst("x-ratelimit-remaining-tokens");
        Optional<Duration> reset = Optional.ofNullable(headers.getFirst("x-ratelimit-reset-tokens"))
                .flatMap(OpenAiCompatibleClient::resetDuration);
        if (remaining == null || reset.isEmpty()) {
            return null;
        }
        try {
            return new TokenWindow(
                    Long.parseLong(remaining.trim()), clock.instant().plus(reset.get()));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private void waitOutRateLimit(HttpHeaders headers, int attempt) {
        Duration wait = rateLimitWait(headers).orElse(policy.retryWait().multipliedBy(attempt));
        if (wait.compareTo(policy.maxRateLimitWait()) > 0) {
            throw ProviderLimitReachedException.waitTooLong(provider.name(), wait, policy.maxRateLimitWait());
        }
        if (attempt > policy.retries()) {
            throw new ProviderLimitReachedException(
                    provider.name(), "it kept limiting the rate, " + attempt + " attempts");
        }
        LOG.info("Provider {} limits the rate; waiting {}", provider.name(), wait);
        sleep(wait);
    }

    /** The wait the provider names: {@code retry-after}, else the longest of the reset headers. */
    private Optional<Duration> rateLimitWait(HttpHeaders headers) {
        Optional<Duration> retryAfter =
                Optional.ofNullable(headers.getFirst("retry-after")).flatMap(OpenAiCompatibleClient::seconds);
        if (retryAfter.isPresent()) {
            return retryAfter;
        }
        return Stream.of("x-ratelimit-reset-tokens", "x-ratelimit-reset-requests")
                .map(headers::getFirst)
                .flatMap(value -> Optional.ofNullable(value).flatMap(OpenAiCompatibleClient::resetDuration).stream())
                .max(Duration::compareTo);
    }

    private static Optional<Duration> seconds(String value) {
        try {
            return Optional.of(Duration.ofMillis(
                    new BigDecimal(value.trim()).movePointRight(3).longValue()));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> resetDuration(String value) {
        Matcher matcher = RESET_DURATION.matcher(value.trim());
        if (value.isBlank() || !matcher.matches()) {
            return Optional.empty();
        }
        Duration total = Duration.ZERO;
        total = total.plusHours(longOf(matcher.group(1)));
        total = total.plusMinutes(longOf(matcher.group(2)));
        if (matcher.group(3) != null) {
            total = total.plusMillis(
                    new BigDecimal(matcher.group(3)).movePointRight(3).longValue());
        }
        return Optional.of(total.plusMillis(longOf(matcher.group(4))));
    }

    private static long longOf(String digits) {
        return digits == null ? 0 : Long.parseLong(digits);
    }

    private void waitBeforeRetry(int attempt, String reason) {
        Duration wait = policy.retryWait().multipliedBy(attempt);
        LOG.warn(
                "Provider {} failed ({}); try {} of {} in {}",
                provider.name(),
                reason,
                attempt + 1,
                policy.retries() + 1,
                wait);
        sleep(wait);
    }

    private void sleep(Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ModelCallException("The wait for provider " + provider.name() + " was interrupted", interrupted);
        }
    }

    private String chatCompletionsUrl() {
        String root = provider.baseUrl();
        return (root.endsWith("/") ? root.substring(0, root.length() - 1) : root) + "/chat/completions";
    }

    private String abbreviated(String body) {
        if (body.isBlank()) {
            return "";
        }
        String oneLine = body.strip().replaceAll("\\s+", " ");
        return ": "
                + (oneLine.length() > policy.maxErrorCharacters()
                        ? oneLine.substring(0, policy.maxErrorCharacters()) + "..."
                        : oneLine);
    }

    private record TokenWindow(long remainingTokens, Instant resetsAt) {}

    private record Reply(int status, HttpHeaders headers, String body) {}
}
