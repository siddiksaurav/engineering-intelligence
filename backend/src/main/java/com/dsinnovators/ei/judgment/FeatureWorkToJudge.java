package com.dsinnovators.ei.judgment;

import java.util.List;
import java.util.Optional;

/**
 * One current Feature work item as the judgment needs it (F07 design, "Main flows" 1).
 *
 * @param itemRowId the evidence item row; the judgment is about this exact row
 * @param key the stable name of the item, such as {@code feature_work:81224:7}
 * @param sourceUrl where the item can be checked: its Feature, or its pull request when it has no work item
 * @param sourceRecordIds the exact raw record versions the item was built from
 * @param featureNumber the work item the item stands under; empty for an item of a pull request alone
 * @param taskNumbers the work items of the person in the group, the Feature itself not among them
 * @param pullRequestExternalIds the pull requests of the item, such as {@code opf/openproject#24204}
 */
public record FeatureWorkToJudge(
        long itemRowId,
        String key,
        String sourceUrl,
        List<Long> sourceRecordIds,
        Optional<Long> featureNumber,
        List<Long> taskNumbers,
        List<String> pullRequestExternalIds) {

    public FeatureWorkToJudge {
        sourceRecordIds = List.copyOf(sourceRecordIds);
        taskNumbers = List.copyOf(taskNumbers);
        pullRequestExternalIds = List.copyOf(pullRequestExternalIds);
    }
}
