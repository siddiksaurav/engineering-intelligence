package com.dsinnovators.ei.job;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * A run cannot survive a restart. At start, before the web server takes requests, every run still
 * recorded as running is marked interrupted, so the job can be started again.
 */
@Component
class InterruptedRunMarker implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(InterruptedRunMarker.class);

    private final JobRunLog log;
    private final Clock clock;

    InterruptedRunMarker(JobRunLog log, Clock clock) {
        this.log = log;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int interrupted = log.markRunningAsInterrupted(clock.instant());
        if (interrupted > 0) {
            LOG.warn("{} job run(s) were going on when the application stopped; marked interrupted", interrupted);
        }
    }
}
