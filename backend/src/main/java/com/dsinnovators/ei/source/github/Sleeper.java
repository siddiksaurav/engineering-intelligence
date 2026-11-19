package com.dsinnovators.ei.source.github;

import java.time.Duration;

/** Waits. A test passes one that only records the wait. */
@FunctionalInterface
interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
