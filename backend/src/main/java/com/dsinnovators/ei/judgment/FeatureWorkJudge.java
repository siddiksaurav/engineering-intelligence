package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.setting.SettingService;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequests;
import com.dsinnovators.ei.source.openproject.StoredWorkPackages;
import com.dsinnovators.ei.source.openproject.WorkPackageText;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Stage 2 for one Feature work item: builds the input, asks the model unless this input was judged
 * before, checks the answer, reads the tier from the complexity table and stores the complexity as a
 * judgment (F07 design, "Main flows" 1). Never gives the model a line count (spec 6.2).
 */
@Component
class FeatureWorkJudge {

    static final String PROMPT_VERSION = "feature-work-complexity/v1";

    private static final String SCHEMA_NAME = "feature_work_complexity";
    private static final String COMPLEXITY_TIERS_SETTING = "evidence.complexity_tiers";

    private final StoredWorkPackages storedWorkPackages;
    private final StoredPullRequests storedPullRequests;
    private final PullRequestDigests pullRequestDigests;
    private final PromptLibrary promptLibrary;
    private final JudgmentModel model;
    private final JudgmentStore judgmentStore;
    private final SettingService settings;
    private final FeatureWorkLimits limits;

    FeatureWorkJudge(
            StoredWorkPackages storedWorkPackages,
            StoredPullRequests storedPullRequests,
            PullRequestDigests pullRequestDigests,
            PromptLibrary promptLibrary,
            JudgmentModel model,
            JudgmentStore judgmentStore,
            SettingService settings,
            JudgmentProperties properties) {
        this.storedWorkPackages = storedWorkPackages;
        this.storedPullRequests = storedPullRequests;
        this.pullRequestDigests = pullRequestDigests;
        this.promptLibrary = promptLibrary;
        this.model = model;
        this.judgmentStore = judgmentStore;
        this.settings = settings;
        this.limits = properties.featureWorkLimits();
    }

    /**
     * Writes the complexity of this row of the item, unless a pull request of the item has no digest
     * yet (counted in {@code items_waiting_for_digest}, no call) or this input was judged before
     * (counted in {@code skipped_same_input}).
     *
     * @param quarter the quarter whose settings apply
     * @throws IllegalStateException when a work item or pull request of the item is not stored, or the
     *     setting {@code evidence.complexity_tiers} has no tier for the complexity
     * @throws InvalidAnswerException when the answer breaks the schema twice
     * @throws UnreadableAnswerException when the answer cannot be read twice
     * @throws ModelLimitReachedException when every provider is at its limit
     */
    void judge(FeatureWorkToJudge item, Quarter quarter, JobCounts counts) {
        Optional<List<FeatureWorkInput.PullRequestPart>> pullRequests = pullRequestsOf(item);
        if (pullRequests.isEmpty()) {
            counts.add("items_waiting_for_digest", 1);
            return;
        }
        FeatureWorkInput input = FeatureWorkInput.of(
                item,
                item.featureNumber().map(this::workItemText),
                item.taskNumbers().stream().map(this::workItemText).toList(),
                pullRequests.get(),
                limits,
                PROMPT_VERSION);
        if (judgmentStore.hasModelJudgment(JudgmentKind.FEATURE_WORK_COMPLEXITY, input.hash())) {
            counts.add("skipped_same_input", 1);
            return;
        }
        CheckedAnswers.Answered<FeatureWorkAnswer> answered = CheckedAnswers.ask(
                model,
                new ModelRequest(
                        PROMPT_VERSION,
                        promptLibrary.instructions(PROMPT_VERSION),
                        input.text(),
                        SCHEMA_NAME,
                        promptLibrary.schema(PROMPT_VERSION)),
                FeatureWorkAnswer::fromAnswer,
                counts);
        FeatureWorkAnswer answer = answered.value();
        judgmentStore.write(new NewJudgment(
                JudgmentKind.FEATURE_WORK_COMPLEXITY,
                JudgmentSubject.evidenceItem(item.itemRowId()),
                answer.toValue(input.judgedOnPartialInput(), input.leftOutPullRequests()),
                tierOf(answer.complexity(), quarter),
                answer.reason(),
                factsOf(answer, input),
                answer.confidence(),
                JudgedBy.MODEL,
                answered.model(),
                PROMPT_VERSION,
                input.hash(),
                null,
                null));
        counts.add("judgments_written", 1);
        if (input.judgedOnPartialInput()) {
            counts.add("judged_on_partial_input", 1);
        }
    }

    /** The pull requests of the item with their digests; empty when any of them has no digest yet. */
    private Optional<List<FeatureWorkInput.PullRequestPart>> pullRequestsOf(FeatureWorkToJudge item) {
        List<FeatureWorkInput.PullRequestPart> parts = new ArrayList<>();
        for (String externalId : item.pullRequestExternalIds()) {
            Optional<PullRequestDigests.CurrentDigest> digest = pullRequestDigests.currentOf(externalId);
            if (digest.isEmpty()) {
                return Optional.empty();
            }
            RawRecord record = storedPullRequest(externalId);
            PullRequestFacts facts = PullRequestFacts.of(record);
            parts.add(new FeatureWorkInput.PullRequestPart(
                    externalId,
                    facts.number(),
                    facts.title(),
                    facts.mergedAt().atOffset(ZoneOffset.UTC).toLocalDate(),
                    facts.url(),
                    PullRequestBody.of(record),
                    digest.get().judgmentId(),
                    digest.get().digest()));
        }
        return Optional.of(parts);
    }

    private RawRecord storedPullRequest(String externalId) {
        return storedPullRequests
                .findByExternalId(externalId)
                .orElseThrow(() -> new IllegalStateException("Pull request " + externalId + " is not stored"));
    }

    private WorkPackageText workItemText(long number) {
        return storedWorkPackages
                .text(number)
                .orElseThrow(() -> new IllegalStateException("Work item " + number + " is not stored"));
    }

    private String tierOf(FeatureWorkAnswer.Complexity complexity, Quarter quarter) {
        JsonNode tier = settings.getJson(COMPLEXITY_TIERS_SETTING, quarter).path(complexity.wireName());
        if (!tier.isString()) {
            throw new IllegalStateException("The setting " + COMPLEXITY_TIERS_SETTING
                    + " has no tier for the complexity " + complexity.wireName());
        }
        return tier.asString();
    }

    private static List<JudgmentFact> factsOf(FeatureWorkAnswer answer, FeatureWorkInput input) {
        return answer.facts().stream()
                .map(fact -> new JudgmentFact(
                        fact.quote().isBlank() ? fact.text() : fact.text() + " (\"" + fact.quote() + "\")",
                        input.linkOf(fact.source())))
                .toList();
    }
}
