package com.dsinnovators.ei.job;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operating settings of the launcher, from {@code ei.jobs} in {@code application.yml} (decision 45).
 *
 * @param countFlushInterval how often the counts of a run are written while it goes on; zero
 *     writes them on every change
 */
@ConfigurationProperties("ei.jobs")
record JobsProperties(Duration countFlushInterval) {

    JobsProperties {
        if (countFlushInterval == null || countFlushInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "ei.jobs.count-flush-interval must be zero or more, was " + countFlushInterval);
        }
    }
}
