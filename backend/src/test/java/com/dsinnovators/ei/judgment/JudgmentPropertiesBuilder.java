package com.dsinnovators.ei.judgment;

import java.time.Duration;
import java.util.List;

/** Builds valid judgment properties as application.yml sets them; a test changes the one number it is about. */
final class JudgmentPropertiesBuilder {

    private JudgmentMode mode = JudgmentMode.PROVIDERS;
    private List<JudgmentProvider> providers = List.of();
    private Duration timeout = Duration.ofMinutes(2);
    private int retries = 3;
    private Duration retryWait = Duration.ofSeconds(2);
    private Duration maxRateLimitWait = Duration.ofSeconds(60);
    private long minRemainingTokens = 6_000;
    private int maxDiffCharacters = 12_000;
    private int maxBodyCharacters = 4_000;
    private int maxErrorCharacters = 300;
    private int maxDescriptionCharacters = 1_500;
    private int maxFeatureWorkCharacters = 14_000;
    private int maxHunkCharacters = 2_000;
    private int maxCommentCharacters = 2_000;
    private int maxRepliedCommentCharacters = 500;
    private int maxReviewCharacters = 12_000;
    private int maxQuoteCharacters = 200;

    static JudgmentPropertiesBuilder properties() {
        return new JudgmentPropertiesBuilder();
    }

    JudgmentPropertiesBuilder mode(JudgmentMode mode) {
        this.mode = mode;
        return this;
    }

    JudgmentPropertiesBuilder providers(List<JudgmentProvider> providers) {
        this.providers = providers;
        return this;
    }

    JudgmentPropertiesBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    JudgmentPropertiesBuilder retries(int retries) {
        this.retries = retries;
        return this;
    }

    JudgmentPropertiesBuilder retryWait(Duration retryWait) {
        this.retryWait = retryWait;
        return this;
    }

    JudgmentPropertiesBuilder maxRateLimitWait(Duration maxRateLimitWait) {
        this.maxRateLimitWait = maxRateLimitWait;
        return this;
    }

    JudgmentPropertiesBuilder minRemainingTokens(long minRemainingTokens) {
        this.minRemainingTokens = minRemainingTokens;
        return this;
    }

    JudgmentPropertiesBuilder maxDiffCharacters(int maxDiffCharacters) {
        this.maxDiffCharacters = maxDiffCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxBodyCharacters(int maxBodyCharacters) {
        this.maxBodyCharacters = maxBodyCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxErrorCharacters(int maxErrorCharacters) {
        this.maxErrorCharacters = maxErrorCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxDescriptionCharacters(int maxDescriptionCharacters) {
        this.maxDescriptionCharacters = maxDescriptionCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxFeatureWorkCharacters(int maxFeatureWorkCharacters) {
        this.maxFeatureWorkCharacters = maxFeatureWorkCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxHunkCharacters(int maxHunkCharacters) {
        this.maxHunkCharacters = maxHunkCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxCommentCharacters(int maxCommentCharacters) {
        this.maxCommentCharacters = maxCommentCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxRepliedCommentCharacters(int maxRepliedCommentCharacters) {
        this.maxRepliedCommentCharacters = maxRepliedCommentCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxReviewCharacters(int maxReviewCharacters) {
        this.maxReviewCharacters = maxReviewCharacters;
        return this;
    }

    JudgmentPropertiesBuilder maxQuoteCharacters(int maxQuoteCharacters) {
        this.maxQuoteCharacters = maxQuoteCharacters;
        return this;
    }

    JudgmentProperties build() {
        return new JudgmentProperties(
                mode,
                providers,
                timeout,
                retries,
                retryWait,
                maxRateLimitWait,
                minRemainingTokens,
                maxDiffCharacters,
                maxBodyCharacters,
                maxErrorCharacters,
                maxDescriptionCharacters,
                maxFeatureWorkCharacters,
                maxHunkCharacters,
                maxCommentCharacters,
                maxRepliedCommentCharacters,
                maxReviewCharacters,
                maxQuoteCharacters);
    }
}
