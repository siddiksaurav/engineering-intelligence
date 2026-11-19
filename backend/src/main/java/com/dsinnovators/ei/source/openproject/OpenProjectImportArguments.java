package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.source.github.GitHubImportArguments;
import com.dsinnovators.ei.source.github.GitHubImportArguments.MergeRange;
import java.util.List;
import java.util.Optional;

/**
 * The arguments of {@code openproject-import} (F03 design, "Starting the job"): the stored pull
 * requests to read, chosen as {@code github-import} chooses them to fetch, and a quarter.
 *
 * @param mergeRange the days of merge to read, both included; empty when numbers are given
 * @param pullRequestNumbers the numbers of stored pull requests; empty when a range is given
 * @param quarter the quarter of the completion step; F03 task 11 reads it
 */
record OpenProjectImportArguments(
        String repository, Optional<MergeRange> mergeRange, List<Integer> pullRequestNumbers, Quarter quarter) {

    private static final String QUARTER = "quarter";

    /**
     * Reads and checks the arguments.
     *
     * @throws IllegalArgumentException that names the argument and its value
     */
    static OpenProjectImportArguments parse(JobArguments arguments) {
        GitHubImportArguments selection = GitHubImportArguments.parse(arguments, List.of(QUARTER));
        return new OpenProjectImportArguments(
                selection.repository(), selection.mergeRange(), selection.pullRequestNumbers(), quarter(arguments));
    }

    private static Quarter quarter(JobArguments arguments) {
        String value = arguments.require(QUARTER);
        try {
            return Quarter.parse(value);
        } catch (IllegalArgumentException notAQuarter) {
            throw new IllegalArgumentException(
                    "The argument '" + QUARTER + "' must be like 2026-Q3, was '" + value + "'", notAQuarter);
        }
    }
}
