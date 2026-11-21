package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Sleeper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** A {@link Sleeper} that adds up the time it waited, so a run can report the waits of its model calls. */
final class WaitMeter implements Sleeper {

    private final Sleeper delegate;
    private final AtomicLong waitedMillis = new AtomicLong();

    WaitMeter(Sleeper delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        delegate.sleep(duration);
        waitedMillis.addAndGet(duration.toMillis());
    }

    /** The time waited since the last {@link #reset()}. */
    Duration waited() {
        return Duration.ofMillis(waitedMillis.get());
    }

    void reset() {
        waitedMillis.set(0);
    }
}
