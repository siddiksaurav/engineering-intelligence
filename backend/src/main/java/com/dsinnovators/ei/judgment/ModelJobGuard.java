package com.dsinnovators.ei.judgment;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Lets one model job run at a time: {@code pull-request-digest}, {@code feature-work-judgment} and
 * {@code review-comment-judgment} share the free tier's token limits, so two at once would spend
 * each other's budget (decision 174). A second job fails at its start naming the one that runs.
 */
@Component
class ModelJobGuard {

    private final AtomicReference<Run> running = new AtomicReference<>();

    /** A job's hold on the guard; close it when the run ends, in a {@code finally} or try-with-resources. */
    final class Run implements AutoCloseable {

        private final String jobName;

        private Run(String jobName) {
            this.jobName = jobName;
        }

        /** Releases the guard; closing a run that is already closed changes nothing. */
        @Override
        public void close() {
            running.compareAndSet(this, null);
        }
    }

    /**
     * Takes the guard for the job.
     *
     * @throws IllegalStateException naming both jobs when another model job holds it
     */
    Run enter(String jobName) {
        Run run = new Run(jobName);
        if (!running.compareAndSet(null, run)) {
            throw new IllegalStateException("The job '" + jobName + "' cannot start: the model job '"
                    + running.get().jobName + "' is running. Start it again when that one has ended");
        }
        return run;
    }
}
