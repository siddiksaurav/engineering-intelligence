package com.dsinnovators.ei.source.github;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the backend talks to GitHub, from {@code ei.github} in {@code application.yml}. Operating
 * numbers, not rules of the evaluation (decision 45).
 *
 * @param apiUrl the REST and GraphQL root, {@code https://api.github.com}
 * @param token a read-only token from {@code GITHUB_TOKEN}; empty when not set. Never shown
 * @param rateLimitReserve calls left in the hourly budget at which the client waits for the reset
 * @param retries how often a call that failed for a passing reason is tried again
 * @param retryWait the wait before the first retry; each further retry waits one more of it
 * @param timeout how long one call may take
 */
@ConfigurationProperties("ei.github")
public record GitHubProperties(
        URI apiUrl, String token, int rateLimitReserve, int retries, Duration retryWait, Duration timeout) {

    public GitHubProperties {
        if (apiUrl == null) {
            throw new IllegalArgumentException("ei.github.api-url is required");
        }
        requireZeroOrMore("ei.github.rate-limit-reserve", rateLimitReserve);
        requireZeroOrMore("ei.github.retries", retries);
        if (retryWait == null || retryWait.isNegative()) {
            throw new IllegalArgumentException("ei.github.retry-wait must be zero or more, was " + retryWait);
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("ei.github.timeout must be more than zero, was " + timeout);
        }
    }

    /** True when a token is set. */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    @Override
    public String toString() {
        return "GitHubProperties[apiUrl=" + apiUrl + ", token=" + (hasToken() ? "(set)" : "(not set)")
                + ", rateLimitReserve=" + rateLimitReserve + ", retries=" + retries + ", retryWait=" + retryWait
                + ", timeout=" + timeout + "]";
    }

    private static void requireZeroOrMore(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be zero or more, was " + value);
        }
    }
}
