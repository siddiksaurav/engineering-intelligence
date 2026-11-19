package com.dsinnovators.ei.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobArgumentsTest {

    private final JobArguments arguments = JobArguments.of(Map.of("repository", "opf/openproject"));

    @Test
    void aGivenArgumentIsFound() {
        assertEquals(Optional.of("opf/openproject"), arguments.get("repository"));
        assertEquals("opf/openproject", arguments.require("repository"));
    }

    @Test
    void anArgumentThatWasNotGivenIsEmpty() {
        assertTrue(arguments.get("mergedFrom").isEmpty());
    }

    @Test
    void aMissingRequiredArgumentIsNamed() {
        IllegalArgumentException missing =
                assertThrows(IllegalArgumentException.class, () -> arguments.require("mergedFrom"));

        assertEquals("The argument 'mergedFrom' is required", missing.getMessage());
    }

    @Test
    void theNamesAreTheGivenNames() {
        assertEquals(Set.of("repository"), arguments.names());
    }

    @Test
    void theArgumentsAreACopy() {
        Map<String, String> values = new HashMap<>(Map.of("repository", "opf/openproject"));
        JobArguments copied = JobArguments.of(values);
        values.put("repository", "other/repository");

        assertEquals("opf/openproject", copied.require("repository"));
        assertEquals(Map.of("repository", "opf/openproject"), copied.asMap());
    }

    @Test
    void aNullValueIsRefusedAndNamed() {
        Map<String, String> values = new HashMap<>();
        values.put("repository", null);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> JobArguments.of(values));

        assertEquals("The argument 'repository' has no value", refused.getMessage());
    }

    @Test
    void aBlankNameIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> JobArguments.of(Map.of(" ", "x")));
    }
}
