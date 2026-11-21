package com.dsinnovators.ei.judgment;

import java.time.Duration;

/** A provider limits the rate for longer than the run waits: it has reached its limit for this run. */
class ProviderLimitReachedException extends ModelCallException {

    private static final long serialVersionUID = 1L;

    private final String providerName;

    ProviderLimitReachedException(String providerName, String reason) {
        super("Provider " + providerName + " has reached its limit: " + reason);
        this.providerName = providerName;
    }

    static ProviderLimitReachedException waitTooLong(String providerName, Duration wait, Duration longestWait) {
        return new ProviderLimitReachedException(
                providerName,
                "it asks for a wait of " + wait.toSeconds() + " s, more than " + longestWait.toSeconds() + " s");
    }

    String providerName() {
        return providerName;
    }
}
