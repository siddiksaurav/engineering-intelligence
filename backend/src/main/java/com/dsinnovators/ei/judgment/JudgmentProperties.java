package com.dsinnovators.ei.judgment;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the backend reaches the model, from {@code ei.judgment} in {@code application.yml}. Operating
 * numbers, not rules of the evaluation (decision 45).
 *
 * @param providers the providers in the order they are tried; one with no key is left out, so the
 *     application starts without any key and a job that needs the model fails at its start
 * @param timeout how long one call may take
 * @param retries how often a call that failed for a passing reason is tried again
 * @param retryWait the wait before the first retry; each further retry waits one more of it
 * @param maxRateLimitWait the longest rate limit wait that is waited out; a longer one puts the
 *     provider at its limit for the run
 * @param minRemainingTokens the tokens a request needs; below this the client waits for the rate limit
 *     window to reset before the next call (decision 134)
 * @param maxDiffCharacters the budget of patch text sent per pull request (decision 155)
 * @param maxBodyCharacters where the body of a pull request is cut
 * @param maxErrorCharacters how much of a provider's error text goes into an exception message
 * @param maxDescriptionCharacters where a work item description or a pull request body is cut in a
 *     Stage 2 input
 * @param maxFeatureWorkCharacters the budget of the whole Feature work input
 * @param maxHunkCharacters how much of the tail of a diff hunk goes into a review call
 * @param maxCommentCharacters where a review comment is cut
 * @param maxRepliedCommentCharacters where the comment that a reply answers is cut
 * @param maxReviewCharacters the budget of one review call; one comment with its parts must fit it
 */
@ConfigurationProperties("ei.judgment")
record JudgmentProperties(
        List<JudgmentProvider> providers,
        Duration timeout,
        int retries,
        Duration retryWait,
        Duration maxRateLimitWait,
        long minRemainingTokens,
        int maxDiffCharacters,
        int maxBodyCharacters,
        int maxErrorCharacters,
        int maxDescriptionCharacters,
        int maxFeatureWorkCharacters,
        int maxHunkCharacters,
        int maxCommentCharacters,
        int maxRepliedCommentCharacters,
        int maxReviewCharacters) {

    JudgmentProperties {
        providers = providersWithAKey(providers);
        requireUnique(providers);
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("ei.judgment.timeout must be more than zero, was " + timeout);
        }
        if (retries < 0) {
            throw new IllegalArgumentException("ei.judgment.retries must be zero or more, was " + retries);
        }
        requireZeroOrMore("ei.judgment.retry-wait", retryWait);
        requireZeroOrMore("ei.judgment.max-rate-limit-wait", maxRateLimitWait);
        if (minRemainingTokens < 0) {
            throw new IllegalArgumentException(
                    "ei.judgment.min-remaining-tokens must be zero or more, was " + minRemainingTokens);
        }
        requireMoreThanZero("ei.judgment.max-diff-characters", maxDiffCharacters);
        requireMoreThanZero("ei.judgment.max-body-characters", maxBodyCharacters);
        requireMoreThanZero("ei.judgment.max-error-characters", maxErrorCharacters);
        requireMoreThanZero("ei.judgment.max-description-characters", maxDescriptionCharacters);
        requireMoreThanZero("ei.judgment.max-feature-work-characters", maxFeatureWorkCharacters);
        requireMoreThanZero("ei.judgment.max-hunk-characters", maxHunkCharacters);
        requireMoreThanZero("ei.judgment.max-comment-characters", maxCommentCharacters);
        requireMoreThanZero("ei.judgment.max-replied-comment-characters", maxRepliedCommentCharacters);
        requireMoreThanZero("ei.judgment.max-review-characters", maxReviewCharacters);
        // Built only to fail the start-up when one comment with its parts cannot fit a call; the fields
        // are not assigned yet here, so the accessor method cannot be used.
        new ReviewCommentLimits(
                maxDescriptionCharacters,
                maxHunkCharacters,
                maxCommentCharacters,
                maxRepliedCommentCharacters,
                maxReviewCharacters);
    }

    DigestLimits digestLimits() {
        return new DigestLimits(maxBodyCharacters, maxDiffCharacters);
    }

    FeatureWorkLimits featureWorkLimits() {
        return new FeatureWorkLimits(maxDescriptionCharacters, maxFeatureWorkCharacters);
    }

    ReviewCommentLimits reviewCommentLimits() {
        return new ReviewCommentLimits(
                maxDescriptionCharacters,
                maxHunkCharacters,
                maxCommentCharacters,
                maxRepliedCommentCharacters,
                maxReviewCharacters);
    }

    CallPolicy callPolicy() {
        return new CallPolicy(retries, retryWait, maxRateLimitWait, minRemainingTokens, maxErrorCharacters);
    }

    private static List<JudgmentProvider> providersWithAKey(List<JudgmentProvider> configured) {
        if (configured == null) {
            return List.of();
        }
        List<JudgmentProvider> withKey = configured.stream()
                .filter(provider ->
                        provider.apiKey() != null && !provider.apiKey().isBlank())
                .toList();
        withKey.forEach(JudgmentProperties::requireComplete);
        return withKey;
    }

    private static void requireComplete(JudgmentProvider provider) {
        if (isBlank(provider.name()) || isBlank(provider.baseUrl()) || isBlank(provider.model())) {
            throw new IllegalArgumentException(
                    "A provider with a key needs a name, a base URL and a model: " + provider);
        }
    }

    private static void requireUnique(List<JudgmentProvider> providers) {
        Set<String> names = new HashSet<>();
        for (JudgmentProvider provider : providers) {
            if (!names.add(provider.name())) {
                throw new IllegalArgumentException("Two providers are named " + provider.name());
            }
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static void requireZeroOrMore(String name, Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be zero or more, was " + value);
        }
    }

    private static void requireMoreThanZero(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be more than zero, was " + value);
        }
    }
}
