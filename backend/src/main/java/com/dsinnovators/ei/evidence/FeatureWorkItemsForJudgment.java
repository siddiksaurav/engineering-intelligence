package com.dsinnovators.ei.evidence;

import com.dsinnovators.ei.judgment.FeatureWorkItems;
import com.dsinnovators.ei.judgment.FeatureWorkToJudge;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Hands the current Feature work items to {@code judgment}, read from their fields and sources. */
@Service
class FeatureWorkItemsForJudgment implements FeatureWorkItems {

    private final EvidenceItemRepository items;

    FeatureWorkItemsForJudgment(EvidenceItemRepository items) {
        this.items = items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeatureWorkToJudge> current() {
        return items.findAllCurrent().stream()
                .filter(item -> item.getEvidenceType() == EvidenceType.FEATURE_WORK)
                .map(FeatureWorkItemsForJudgment::toJudge)
                .toList();
    }

    private static FeatureWorkToJudge toJudge(EvidenceItem item) {
        JsonNode fields = item.getFields();
        Optional<Long> featureNumber = fields.path("feature").has("number")
                ? Optional.of(fields.path("feature").path("number").asLong())
                : Optional.empty();
        return new FeatureWorkToJudge(
                item.getId(),
                item.getItemKey(),
                item.getSourceUrl(),
                item.getSources().stream()
                        .map(EvidenceItemSource::rawRecordId)
                        .sorted()
                        .toList(),
                featureNumber,
                taskNumbers(fields, featureNumber),
                pullRequestExternalIds(fields));
    }

    // The Feature itself is a task of its own group when a pull request links it directly; it is given as the feature.
    private static List<Long> taskNumbers(JsonNode fields, Optional<Long> featureNumber) {
        List<Long> numbers = new ArrayList<>();
        for (JsonNode task : fields.path("tasks")) {
            long number = task.path("number").asLong();
            if (!featureNumber.equals(Optional.of(number))) {
                numbers.add(number);
            }
        }
        return numbers;
    }

    private static List<String> pullRequestExternalIds(JsonNode fields) {
        List<String> externalIds = new ArrayList<>();
        fields.path("pullRequests")
                .forEach(pullRequest ->
                        externalIds.add(pullRequest.path("externalId").asString()));
        return externalIds;
    }
}
