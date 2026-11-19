package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.job.JobArguments;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The arguments of {@code github-import} (F02 design 5.2): a repository and either a range of
 * merge dates or a list of pull request numbers.
 *
 * @param mergeRange the days to list, both included; empty when numbers are given
 * @param pullRequestNumbers the numbers to fetch, in the given order; empty when a range is given
 */
public record GitHubImportArguments(
        String repository, Optional<MergeRange> mergeRange, List<Integer> pullRequestNumbers) {

    public record MergeRange(LocalDate from, LocalDate to) {}

    static final String REPOSITORY = "repository";
    static final String MERGED_FROM = "mergedFrom";
    static final String MERGED_TO = "mergedTo";
    static final String PULL_REQUEST_NUMBERS = "pullRequestNumbers";

    private static final List<String> KNOWN = List.of(REPOSITORY, MERGED_FROM, MERGED_TO, PULL_REQUEST_NUMBERS);
    private static final Pattern OWNER_SLASH_NAME = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern NUMBER_LIST = Pattern.compile("\\s*[1-9][0-9]{0,9}\\s*(,\\s*[1-9][0-9]{0,9}\\s*)*");

    /**
     * Reads and checks the arguments.
     *
     * @throws IllegalArgumentException that names the argument and its value
     */
    static GitHubImportArguments parse(JobArguments arguments) {
        return parse(arguments, List.of());
    }

    /**
     * Reads and checks the arguments of a job that takes these and some of its own.
     *
     * @param ownArguments the names the calling job reads itself; they are not refused here
     * @throws IllegalArgumentException that names the argument and its value
     */
    public static GitHubImportArguments parse(JobArguments arguments, List<String> ownArguments) {
        List<String> known =
                Stream.concat(KNOWN.stream(), ownArguments.stream()).toList();
        for (String name : arguments.names()) {
            if (!known.contains(name)) {
                throw new IllegalArgumentException(
                        "Unknown argument '" + name + "'. Known: " + String.join(", ", known));
            }
        }
        String repository = arguments.require(REPOSITORY);
        if (!OWNER_SLASH_NAME.matcher(repository).matches()) {
            throw new IllegalArgumentException(
                    "The argument '" + REPOSITORY + "' must be owner/name, was '" + repository + "'");
        }
        boolean range = arguments.get(MERGED_FROM).isPresent()
                || arguments.get(MERGED_TO).isPresent();
        boolean numbers = arguments.get(PULL_REQUEST_NUMBERS).isPresent();
        if (range && numbers) {
            throw new IllegalArgumentException("Give either mergedFrom and mergedTo, or pullRequestNumbers, not both");
        }
        if (range) {
            return new GitHubImportArguments(repository, Optional.of(mergeRange(arguments)), List.of());
        }
        if (numbers) {
            return new GitHubImportArguments(
                    repository, Optional.empty(), numbers(arguments.require(PULL_REQUEST_NUMBERS)));
        }
        throw new IllegalArgumentException("Give mergedFrom and mergedTo, or pullRequestNumbers");
    }

    private static MergeRange mergeRange(JobArguments arguments) {
        LocalDate from = date(arguments, MERGED_FROM, MERGED_TO);
        LocalDate to = date(arguments, MERGED_TO, MERGED_FROM);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "The argument '" + MERGED_TO + "' (" + to + ") is before '" + MERGED_FROM + "' (" + from + ")");
        }
        return new MergeRange(from, to);
    }

    private static LocalDate date(JobArguments arguments, String name, String other) {
        String value = arguments
                .get(name)
                .orElseThrow(() ->
                        new IllegalArgumentException("The argument '" + name + "' is required with '" + other + "'"));
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException notADate) {
            throw new IllegalArgumentException(
                    "The argument '" + name + "' must be a date like 2026-07-01, was '" + value + "'", notADate);
        }
    }

    private static List<Integer> numbers(String value) {
        if (!NUMBER_LIST.matcher(value).matches()) {
            throw numbersRefused(value);
        }
        Set<Integer> numbers = new LinkedHashSet<>();
        for (String number : value.replace(" ", "").split(",", -1)) {
            try {
                numbers.add(Integer.parseInt(number.trim()));
            } catch (NumberFormatException tooLarge) {
                throw numbersRefused(value);
            }
        }
        return new ArrayList<>(numbers);
    }

    private static IllegalArgumentException numbersRefused(String value) {
        return new IllegalArgumentException("The argument '" + PULL_REQUEST_NUMBERS
                + "' must be positive numbers separated by commas, was '" + value + "'");
    }
}
