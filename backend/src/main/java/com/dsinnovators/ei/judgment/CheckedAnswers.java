package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.job.JobCounts;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Asks the model and checks the answer. An answer that breaks the schema or cannot be read is asked
 * for once more (decision 135); the second refusal reaches the caller. Shared by every judge, so the
 * rule is in one place.
 */
final class CheckedAnswers {

    private static final Logger LOG = LoggerFactory.getLogger(CheckedAnswers.class);

    /** An answer that passed its check, with the model id the provider answered with. */
    record Answered<T>(T value, String model) {}

    private CheckedAnswers() {}

    /**
     * Counts every call and its tokens in {@code model_calls}, {@code input_tokens} and {@code output_tokens}.
     *
     * @param checker reads the answer and throws {@link InvalidAnswerException} when it breaks the schema
     * @throws InvalidAnswerException when the answer breaks the schema twice
     * @throws UnreadableAnswerException when the answer cannot be read twice
     * @throws ModelLimitReachedException when every provider is at its limit
     */
    static <T> Answered<T> ask(
            JudgmentModel model, ModelRequest request, Function<JsonNode, T> checker, JobCounts counts) {
        try {
            return askOnce(model, request, checker, counts);
        } catch (InvalidAnswerException | UnreadableAnswerException firstRefusal) {
            LOG.warn("The model's answer was refused, asking once more: {}", firstRefusal.getMessage());
            model.answerRefused(request, firstRefusal.getMessage());
            return askOnce(model, request, checker, counts);
        }
    }

    private static <T> Answered<T> askOnce(
            JudgmentModel model, ModelRequest request, Function<JsonNode, T> checker, JobCounts counts) {
        ModelAnswer answer = model.judge(request);
        counts.add("model_calls", 1);
        counts.add("input_tokens", answer.inputTokens());
        counts.add("output_tokens", answer.outputTokens());
        return new Answered<>(checker.apply(answer.answer()), answer.model());
    }
}
