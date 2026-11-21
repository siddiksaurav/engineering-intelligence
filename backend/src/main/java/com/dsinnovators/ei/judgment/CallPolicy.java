package com.dsinnovators.ei.judgment;

import java.time.Duration;

/**
 * How a client treats a call that failed for a passing reason.
 *
 * @param retries how often a call is tried again
 * @param retryWait the wait before the first retry; each further retry waits one more of it
 * @param maxRateLimitWait the longest rate limit wait that is waited out; a longer one means the
 *     provider has reached its limit
 * @param minRemainingTokens the tokens a request needs; when the provider reports fewer left in its
 *     rate limit window, the next call waits for the window to reset
 * @param maxErrorCharacters how much of a provider's error text goes into an exception message
 */
record CallPolicy(
        int retries, Duration retryWait, Duration maxRateLimitWait, long minRemainingTokens, int maxErrorCharacters) {}
