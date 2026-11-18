package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobRegistryTest {

    private record NamedJob(String name) implements Job {
        @Override
        public void run(JobArguments arguments) {}
    }

    @Test
    void aJobIsFoundByItsName() {
        Job importJob = new NamedJob("import");

        JobRegistry registry = new JobRegistry(List.of(importJob, new NamedJob("judge")));

        assertSame(importJob, registry.find("import").orElseThrow());
    }

    @Test
    void anUnknownNameFindsNothing() {
        assertTrue(new JobRegistry(List.of(new NamedJob("import"))).find("nope").isEmpty());
    }

    @Test
    void namesAreSorted() {
        JobRegistry registry = new JobRegistry(List.of(new NamedJob("judge"), new NamedJob("import")));

        assertEquals(List.of("import", "judge"), registry.names());
    }

    @Test
    void twoJobsWithOneNameAreRejectedAndTheNameIsGiven() {
        IllegalStateException rejected = assertThrows(
                IllegalStateException.class,
                () -> new JobRegistry(List.of(new NamedJob("import"), new NamedJob("import"))));

        assertTrue(rejected.getMessage().contains("import"));
    }

    @Test
    void anApplicationWithoutJobsHasAnEmptyRegistry() {
        assertEquals(List.of(), new JobRegistry(List.of()).names());
    }
}
