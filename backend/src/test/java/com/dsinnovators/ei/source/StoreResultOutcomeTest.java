package com.dsinnovators.ei.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.source.StoreResult.Outcome;
import org.junit.jupiter.api.Test;

class StoreResultOutcomeTest {

    @Test
    void theCountNameJoinsTheRecordKindAndTheOutcomeInLowerCase() {
        assertEquals("work_package.created", Outcome.CREATED.countName("work_package"));
        assertEquals("pull_request.new_version", Outcome.NEW_VERSION.countName("pull_request"));
        assertEquals("project.unchanged", Outcome.UNCHANGED.countName("project"));
    }
}
