package com.dsinnovators.ei.source.openproject;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the backend talks to OpenProject, from {@code ei.openproject} in {@code application.yml}.
 * Operating numbers, not rules of the evaluation (decision 45).
 *
 * @param apiUrl the API root, {@code https://community.openproject.org/api/v3}
 * @param pageSize elements asked for in one collection call
 * @param pause the wait between two calls, to be polite to a public server
 * @param retries how often a call that failed for a passing reason is tried again
 * @param retryWait the wait before the first retry; each further retry waits one more of it
 * @param timeout how long one call may take
 */
@ConfigurationProperties("ei.openproject")
public record OpenProjectProperties(
        URI apiUrl, int pageSize, Duration pause, int retries, Duration retryWait, Duration timeout) {

    // OpenProject's own limit for a page.
    private static final int MAXIMUM_PAGE_SIZE = 1000;

    public OpenProjectProperties {
        if (apiUrl == null) {
            throw new IllegalArgumentException("ei.openproject.api-url is required");
        }
        if (pageSize < 1 || pageSize > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "ei.openproject.page-size must be from 1 to " + MAXIMUM_PAGE_SIZE + ", was " + pageSize);
        }
        requireZeroOrMore("ei.openproject.pause", pause);
        if (retries < 0) {
            throw new IllegalArgumentException("ei.openproject.retries must be zero or more, was " + retries);
        }
        requireZeroOrMore("ei.openproject.retry-wait", retryWait);
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("ei.openproject.timeout must be more than zero, was " + timeout);
        }
    }

    private static void requireZeroOrMore(String name, Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be zero or more, was " + value);
        }
    }
}
