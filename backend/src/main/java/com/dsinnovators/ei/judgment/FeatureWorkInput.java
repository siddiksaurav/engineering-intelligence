package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.openproject.WorkPackageText;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the model reads of one Feature work item (F07 design, "The Feature work input", decisions 167
 * and 168): the work item texts and the digests of the pull requests, each part tagged with a source
 * key the model cites. No line count, no file size and no diff is ever part of it.
 *
 * @param text the user message sent to the model
 * @param hash SHA-256 over the kind, the prompt version, the text, the item row, its source records and
 *     the digests it rests on; the same hash is never judged twice (decision 157)
 * @param judgedOnPartialInput true when pull requests were left out for size; set by the system
 * @param leftOutPullRequests the external ids of the pull requests left out, oldest merge first
 */
record FeatureWorkInput(
        String text,
        String hash,
        boolean judgedOnPartialInput,
        List<String> leftOutPullRequests,
        Map<String, String> sourceLinks,
        String itemSourceUrl) {

    private static final String RULE = "This is Feature work. The tracker type decides that; the work type is yours.";

    /**
     * One merged pull request of the item with its digest.
     *
     * @param body only given to the model for an item without a work item
     * @param digestJudgmentId the digest row, so that a new digest gives a new hash
     */
    record PullRequestPart(
            String externalId,
            int number,
            String title,
            LocalDate mergedOn,
            String url,
            String body,
            long digestJudgmentId,
            PullRequestDigest digest) {

        String key() {
            return "PR-" + number;
        }
    }

    /**
     * Builds the input.
     *
     * @param feature the work item the item stands under; empty for an item without a work item
     * @param tasks the other work items of the person in the group
     * @param pullRequests the pull requests of the item, in any order
     */
    static FeatureWorkInput of(
            FeatureWorkToJudge item,
            Optional<WorkPackageText> feature,
            List<WorkPackageText> tasks,
            List<PullRequestPart> pullRequests,
            FeatureWorkLimits limits,
            String promptVersion) {
        List<PullRequestPart> ordered = pullRequests.stream()
                .sorted(Comparator.comparing(PullRequestPart::mergedOn).thenComparingInt(PullRequestPart::number))
                .toList();
        Composer composer = new Composer(feature, tasks, ordered, limits.maxDescriptionCharacters());
        Composer.Composition fitted = composer.fit(limits.maxFeatureWorkCharacters());
        List<PullRequestPart> kept = ordered.subList(0, fitted.pullRequestsKept());
        List<String> leftOut = ordered.subList(fitted.pullRequestsKept(), ordered.size()).stream()
                .map(PullRequestPart::externalId)
                .toList();
        return new FeatureWorkInput(
                fitted.text(),
                hashOf(item, ordered, fitted.text(), promptVersion),
                !leftOut.isEmpty(),
                leftOut,
                sourceLinks(feature, tasks, kept),
                item.sourceUrl());
    }

    /** The link of the source the model cited, or the link of the item when the key is empty or not in the input. */
    String linkOf(String sourceKey) {
        return sourceLinks.getOrDefault(sourceKey.trim().toUpperCase(Locale.ROOT), itemSourceUrl);
    }

    private static Map<String, String> sourceLinks(
            Optional<WorkPackageText> feature, List<WorkPackageText> tasks, List<PullRequestPart> kept) {
        Map<String, String> links = new HashMap<>();
        feature.ifPresent(workItem -> links.put(workItemKey(workItem), workItem.url()));
        tasks.forEach(workItem -> links.put(workItemKey(workItem), workItem.url()));
        kept.forEach(pullRequest -> links.put(pullRequest.key(), pullRequest.url()));
        return Map.copyOf(links);
    }

    private static String workItemKey(WorkPackageText workItem) {
        return "WI-" + workItem.number();
    }

    private static String hashOf(
            FeatureWorkToJudge item, List<PullRequestPart> pullRequests, String text, String promptVersion) {
        ObjectNode hashed = JsonNodeFactory.instance.objectNode();
        hashed.put("kind", JudgmentKind.FEATURE_WORK_COMPLEXITY.databaseName());
        hashed.put("promptVersion", promptVersion);
        hashed.put("input", text);
        hashed.put("itemRowId", item.itemRowId());
        ArrayNode records = hashed.putArray("sourceRecordIds");
        item.sourceRecordIds().forEach(records::add);
        ArrayNode digests = hashed.putArray("digestJudgmentIds");
        pullRequests.stream().map(PullRequestPart::digestJudgmentId).sorted().forEach(digests::add);
        return CanonicalJson.sha256Hex(hashed);
    }

    /** Writes the text for given cuts and finds the cuts that make it fit (decision 168). */
    private static final class Composer {

        record Composition(String text, int pullRequestsKept) {}

        private final Optional<WorkPackageText> feature;
        private final List<WorkPackageText> tasks;
        private final List<PullRequestPart> pullRequests;
        private final int maxDescriptionCharacters;

        Composer(
                Optional<WorkPackageText> feature,
                List<WorkPackageText> tasks,
                List<PullRequestPart> pullRequests,
                int maxDescriptionCharacters) {
            this.feature = feature;
            this.tasks = tasks;
            this.pullRequests = pullRequests;
            this.maxDescriptionCharacters = maxDescriptionCharacters;
        }

        /**
         * The task descriptions are cut first, then the feature description; when the pull requests
         * alone do not fit, the newest ones are left out. The oldest pull request always stays.
         */
        Composition fit(int budget) {
            int total = pullRequests.size();
            int fullTaskCap =
                    Math.min(maxDescriptionCharacters, longest(tasks.stream().map(WorkPackageText::description)));
            int fullFeatureCap = Math.min(maxDescriptionCharacters, longestFeatureText());
            int taskCap = shrunk(fullTaskCap, budget, cap -> compose(cap, fullFeatureCap, total));
            int featureCap = shrunk(fullFeatureCap, budget, cap -> compose(taskCap, cap, total));
            int kept = total;
            String text = compose(taskCap, featureCap, kept);
            while (text.length() > budget && kept > 1) {
                kept--;
                text = compose(taskCap, featureCap, kept);
            }
            return new Composition(text, kept);
        }

        /** Halves the cap while the text is over the budget and a halved cap still makes it shorter. */
        private static int shrunk(int cap, int budget, IntFunction<String> textWithCap) {
            int current = cap;
            String text = textWithCap.apply(current);
            while (text.length() > budget && current > 0) {
                String shorter = textWithCap.apply(current / 2);
                if (shorter.length() >= text.length()) {
                    break;
                }
                current /= 2;
                text = shorter;
            }
            return current;
        }

        private int longestFeatureText() {
            return feature.isPresent()
                    ? feature.get().description().length()
                    : longest(pullRequests.stream().map(PullRequestPart::body));
        }

        private static int longest(Stream<String> texts) {
            return texts.mapToInt(String::length).max().orElse(0);
        }

        private String compose(int taskCap, int featureCap, int kept) {
            StringBuilder text = new StringBuilder(RULE).append("\n\n");
            if (feature.isPresent()) {
                appendWorkItem(text, feature.get(), "feature", featureCap);
            } else {
                text.append("No work item: the pull requests below are the whole item.\n\n");
            }
            tasks.forEach(task -> appendWorkItem(text, task, "task of the person", taskCap));
            pullRequests.subList(0, kept).forEach(pullRequest -> appendPullRequest(text, pullRequest, featureCap));
            if (kept < pullRequests.size()) {
                text.append("Left out for size: ")
                        .append(pullRequests.subList(kept, pullRequests.size()).stream()
                                .map(PullRequestPart::key)
                                .collect(Collectors.joining(", ")))
                        .append(". The judgment rests on partial input.\n");
            }
            return text.toString();
        }

        private static void appendWorkItem(StringBuilder text, WorkPackageText workItem, String role, int cap) {
            text.append("[WI-").append(workItem.number()).append("] ").append(workItem.title());
            text.append("\nRole: ").append(role);
            text.append("\nType: ").append(workItem.type());
            workItem.priority()
                    .ifPresent(priority -> text.append("\nPriority: ").append(priority));
            workItem.parentTitle().ifPresent(parent -> text.append("\nParent: ").append(parent));
            appendDescription(text, workItem.description(), cap);
            text.append('\n');
        }

        private void appendPullRequest(StringBuilder text, PullRequestPart pullRequest, int bodyCap) {
            PullRequestDigest digest = pullRequest.digest();
            text.append('[').append(pullRequest.key()).append("] ").append(pullRequest.title());
            text.append("\nMerged: ").append(pullRequest.mergedOn());
            text.append("\nDigest: work type ").append(digest.workType().wireName());
            text.append("; modules ")
                    .append(digest.modulesTouched().isEmpty() ? "none" : String.join(", ", digest.modulesTouched()));
            text.append("; schema or data migration ").append(yesNo(digest.schemaOrDataMigration()));
            text.append("; new integration ").append(yesNo(digest.newIntegration()));
            text.append("; dependency changes ").append(yesNo(digest.dependencyChanges()));
            text.append("; test kind ").append(digest.testKind().wireName());
            text.append("; code read in full ").append(yesNo(!digest.judgedOnPartialCode()));
            text.append("\nNature of the change: ").append(digest.natureOfChange());
            if (feature.isEmpty()) {
                appendDescription(text, pullRequest.body(), bodyCap);
            }
            text.append("\n\n");
        }

        private static void appendDescription(StringBuilder text, String description, int cap) {
            if (description.isBlank()) {
                text.append("\nDescription: none");
            } else {
                text.append("\nDescription:\n").append(TextCuts.cut(description, cap));
            }
        }

        private static String yesNo(boolean value) {
            return value ? "yes" : "no";
        }
    }
}
