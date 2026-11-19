package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.job.JobArguments;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubImportArgumentsTest {

    private static GitHubImportArguments parse(Map<String, String> values) {
        return GitHubImportArguments.parse(JobArguments.of(values));
    }

    private static String refusal(Map<String, String> values) {
        return assertThrows(IllegalArgumentException.class, () -> parse(values)).getMessage();
    }

    @Test
    void aRangeOfMergeDatesIsRead() {
        GitHubImportArguments arguments =
                parse(Map.of("repository", "opf/openproject", "mergedFrom", "2026-07-01", "mergedTo", "2026-07-14"));

        assertEquals("opf/openproject", arguments.repository());
        assertEquals(
                Optional.of(new GitHubImportArguments.MergeRange(
                        LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-14"))),
                arguments.mergeRange());
        assertEquals(List.of(), arguments.pullRequestNumbers());
    }

    @Test
    void numbersAreReadInOrderWithoutRepeats() {
        GitHubImportArguments arguments =
                parse(Map.of("repository", "opf/openproject", "pullRequestNumbers", " 24204, 24201,24204 "));

        assertEquals(List.of(24204, 24201), arguments.pullRequestNumbers());
        assertTrue(arguments.mergeRange().isEmpty());
    }

    @Test
    void theRepositoryIsRequired() {
        assertEquals("The argument 'repository' is required", refusal(Map.of("pullRequestNumbers", "1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"openproject", "opf/", "/openproject", "opf/open/project", "opf /openproject"})
    void aRepositoryThatIsNotOwnerSlashNameIsRefused(String repository) {
        assertEquals(
                "The argument 'repository' must be owner/name, was '" + repository + "'",
                refusal(Map.of("repository", repository, "pullRequestNumbers", "1")));
    }

    @Test
    void bothFormsAtOnceAreRefused() {
        assertEquals(
                "Give either mergedFrom and mergedTo, or pullRequestNumbers, not both",
                refusal(Map.of(
                        "repository", "opf/openproject",
                        "mergedFrom", "2026-07-01",
                        "mergedTo", "2026-07-14",
                        "pullRequestNumbers", "1")));
    }

    @Test
    void neitherFormIsRefused() {
        assertEquals(
                "Give mergedFrom and mergedTo, or pullRequestNumbers",
                refusal(Map.of("repository", "opf/openproject")));
    }

    @Test
    void halfARangeIsRefused() {
        assertEquals(
                "The argument 'mergedTo' is required with 'mergedFrom'",
                refusal(Map.of("repository", "opf/openproject", "mergedFrom", "2026-07-01")));
    }

    @Test
    void aDateInAnotherFormIsRefused() {
        assertEquals(
                "The argument 'mergedFrom' must be a date like 2026-07-01, was '01.07.2026'",
                refusal(Map.of("repository", "opf/openproject", "mergedFrom", "01.07.2026", "mergedTo", "2026-07-14")));
    }

    @Test
    void anEndBeforeTheStartIsRefused() {
        assertEquals(
                "The argument 'mergedTo' (2026-07-01) is before 'mergedFrom' (2026-07-14)",
                refusal(Map.of("repository", "opf/openproject", "mergedFrom", "2026-07-14", "mergedTo", "2026-07-01")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "1,,2", "0", "-3", "1.5", "99999999999"})
    void numbersThatAreNotPositiveWholeNumbersAreRefused(String numbers) {
        assertEquals(
                "The argument 'pullRequestNumbers' must be positive numbers separated by commas, was '" + numbers + "'",
                refusal(Map.of("repository", "opf/openproject", "pullRequestNumbers", numbers)));
    }

    @Test
    void anUnknownArgumentIsRefusedAndTheKnownOnesAreNamed() {
        assertEquals(
                "Unknown argument 'since'. Known: repository, mergedFrom, mergedTo, pullRequestNumbers",
                refusal(Map.of("repository", "opf/openproject", "pullRequestNumbers", "1", "since", "x")));
    }
}
