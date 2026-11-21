package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.JobCounts;
import com.dsinnovators.ei.source.RawRecord;
import com.dsinnovators.ei.source.github.PullRequestDiff;
import com.dsinnovators.ei.source.github.PullRequestFacts;
import com.dsinnovators.ei.source.github.StoredPullRequestDiffs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Stage 1 for one pull request: builds the input, asks the model unless this input was judged
 * before, checks the answer and stores the digest as a judgment (F06 design, "Main flows" 1). The
 * only write is one judgment row, which the store makes in one transaction.
 */
@Component
class PullRequestDigester {

    static final String PROMPT_VERSION = "pull-request-digest/v1";

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestDigester.class);
    private static final String SCHEMA_NAME = "pull_request_digest";

    private record Answered(PullRequestDigest digest, String model) {}

    private final StoredPullRequestDiffs storedDiffs;
    private final DiffFileFilter diffFileFilter;
    private final PromptLibrary promptLibrary;
    private final JudgmentModel model;
    private final JudgmentStore judgmentStore;
    private final DigestLimits limits;

    PullRequestDigester(
            StoredPullRequestDiffs storedDiffs,
            DiffFileFilter diffFileFilter,
            PromptLibrary promptLibrary,
            JudgmentModel model,
            JudgmentStore judgmentStore,
            JudgmentProperties properties) {
        this.storedDiffs = storedDiffs;
        this.diffFileFilter = diffFileFilter;
        this.promptLibrary = promptLibrary;
        this.model = model;
        this.judgmentStore = judgmentStore;
        this.limits = properties.digestLimits();
    }

    /**
     * Writes the digest of this version of the pull request, superseding the current digest of the
     * pull request.
     *
     * @throws IllegalStateException when no diff record is stored for the pull request
     * @throws InvalidAnswerException when the answer breaks the schema twice
     * @throws UnreadableAnswerException when the answer cannot be read twice
     * @throws ModelLimitReachedException when every provider is at its limit
     */
    void digest(RawRecord pullRequest, Quarter quarter, JobCounts counts) {
        String externalId = pullRequest.getExternalId();
        PullRequestFacts facts = PullRequestFacts.of(pullRequest);
        PullRequestDiff diff = storedDiffs
                .latestOf(externalId)
                .orElseThrow(() -> new IllegalStateException("No diff record is stored for " + externalId));
        DigestInput input = DigestInput.of(
                pullRequest.getId(),
                facts.title(),
                bodyOf(pullRequest),
                diff,
                diffFileFilter.select(diff, quarter),
                limits,
                PROMPT_VERSION);
        if (judgmentStore.hasModelJudgment(JudgmentKind.PULL_REQUEST_DIGEST, input.hash())) {
            counts.add("skipped_same_input", 1);
            return;
        }
        Answered answered = judge(input, counts);
        Long supersedesId = judgmentStore
                .findCurrentOfPullRequest(JudgmentKind.PULL_REQUEST_DIGEST, externalId)
                .map(Judgment::getId)
                .orElse(null);
        PullRequestDigest digest = answered.digest();
        judgmentStore.write(new NewJudgment(
                JudgmentKind.PULL_REQUEST_DIGEST,
                JudgmentSubject.pullRequestRecord(pullRequest.getId()),
                digest.toValue(),
                null,
                digest.reason(),
                DigestFacts.of(digest.facts(), facts.url(), diff),
                JudgedBy.MODEL,
                answered.model(),
                PROMPT_VERSION,
                input.hash(),
                null,
                supersedesId));
        counts.add("digests_written", 1);
        if (input.judgedOnPartialCode()) {
            counts.add("judged_on_partial_code", 1);
        }
    }

    /** Asks the model; an answer that breaks the schema or cannot be read is asked for once more (decision 135). */
    private Answered judge(DigestInput input, JobCounts counts) {
        ModelRequest request = new ModelRequest(
                PROMPT_VERSION,
                promptLibrary.instructions(PROMPT_VERSION),
                input.text(),
                SCHEMA_NAME,
                promptLibrary.schema(PROMPT_VERSION));
        try {
            return ask(request, input.judgedOnPartialCode(), counts);
        } catch (InvalidAnswerException | UnreadableAnswerException firstRefusal) {
            LOG.warn("The model's answer was refused, asking once more: {}", firstRefusal.getMessage());
            return ask(request, input.judgedOnPartialCode(), counts);
        }
    }

    private Answered ask(ModelRequest request, boolean judgedOnPartialCode, JobCounts counts) {
        ModelAnswer answer = model.judge(request);
        counts.add("model_calls", 1);
        counts.add("input_tokens", answer.inputTokens());
        counts.add("output_tokens", answer.outputTokens());
        return new Answered(PullRequestDigest.fromAnswer(answer.answer(), judgedOnPartialCode), answer.model());
    }

    private static String bodyOf(RawRecord pullRequest) {
        JsonNode body = pullRequest.getPayload().path("body");
        return body.isString() ? body.asString() : "";
    }
}
