package com.dsinnovators.ei.judgment;

import java.time.Duration;

/**
 * How a client treats a call that failed for a passing reason.
 *
 * @param retries how often a call is tried again
 * @param retryWait the wait before the first retry; each further retry waits one more of it
 * @param maxRateLimitWait the longest rate limit wait that is waited out; a longer one means the
 *     provider has reached its limit
 * @param maxErrorCharacters how much of a provider's error text goes into an exception message
 */
record CallPolicy(int retries, Duration retryWait, Duration maxRateLimitWait, int maxErrorCharacters) {}
