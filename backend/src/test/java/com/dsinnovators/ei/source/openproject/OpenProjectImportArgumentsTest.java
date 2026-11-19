package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobArguments;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenProjectImportArgumentsTest {

    private static OpenProjectImportArguments parse(Map<String, String> values) {
        return OpenProjectImportArguments.parse(JobArguments.of(values));
    }

    private static String refusal(Map<String, String> values) {
        return assertThrows(IllegalArgumentException.class, () -> parse(values)).getMessage();
    }

    @Test
    void aRangeAndAQuarterAreRead() {
        OpenProjectImportArguments arguments = parse(Map.of(
                "repository", "opf/openproject",
                "mergedFrom", "2026-07-01",
                "mergedTo", "2026-07-14",
                "quarter", "2026-Q3"));

        assertEquals("opf/openproject", arguments.repository());
        assertEquals(
                LocalDate.parse("2026-07-14"),
                arguments.mergeRange().orElseThrow().to());
        assertEquals(List.of(), arguments.pullRequestNumbers());
        assertEquals(new Quarter(2026, 3), arguments.quarter());
    }

    @Test
    void numbersAndAQuarterAreRead() {
        OpenProjectImportArguments arguments = parse(
                Map.of("repository", "opf/openproject", "pullRequestNumbers", "26057, 26061", "quarter", "2026-Q3"));

        assertEquals(List.of(26057, 26061), arguments.pullRequestNumbers());
    }

    @Test
    void refusesARangeAndNumbersTogetherAndAMissingQuarter() {
        assertEquals(
                "Give either mergedFrom and mergedTo, or pullRequestNumbers, not both",
                refusal(Map.of(
                        "repository", "opf/openproject",
                        "mergedFrom", "2026-07-01",
                        "mergedTo", "2026-07-14",
                        "pullRequestNumbers", "1",
                        "quarter", "2026-Q3")));
        assertEquals(
                "The argument 'quarter' is required",
                refusal(Map.of("repository", "opf/openproject", "pullRequestNumbers", "1")));
    }

    @Test
    void aQuarterThatIsNotAQuarterIsRefusedNamingTheArgument() {
        assertEquals(
                "The argument 'quarter' must be like 2026-Q3, was '2026-Q5'",
                refusal(Map.of("repository", "opf/openproject", "pullRequestNumbers", "1", "quarter", "2026-Q5")));
    }

    @Test
    void anUnknownArgumentIsRefusedNamingTheKnownOnes() {
        assertEquals(
                "Unknown argument 'qarter'. Known: repository, mergedFrom, mergedTo, pullRequestNumbers, quarter",
                refusal(Map.of("repository", "opf/openproject", "pullRequestNumbers", "1", "qarter", "2026-Q3")));
    }
}
