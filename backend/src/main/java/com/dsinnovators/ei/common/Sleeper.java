package com.dsinnovators.ei.common;

import java.time.Duration;

/** Waits. A test passes one that only records the wait. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
