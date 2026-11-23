package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.job.JobCounts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class CheckedAnswersTest {

    private static final ModelRequest REQUEST =
            new ModelRequest("test/v1", "Instructions", "Input", "schema", CanonicalJson.parse("{}"));
    private static final JsonNode GOOD = CanonicalJson.parse("{\"ok\":true}");
    private static final JsonNode BAD = CanonicalJson.parse("{\"ok\":false}");

    private final Map<String, Long> counts = new HashMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);
    private final FakeJudgmentModel model = new FakeJudgmentModel();

    private static boolean check(JsonNode answer) {
        if (!answer.path("ok").asBoolean()) {
            throw new InvalidAnswerException("ok must be true");
        }
        return true;
    }

    @Test
    void returnsTheCheckedAnswerWithTheModelAndCountsTheCall() {
        model.thenAnswers(GOOD);

        CheckedAnswers.Answered<Boolean> answered =
                CheckedAnswers.ask(model, REQUEST, CheckedAnswersTest::check, jobCounts);

        assertEquals(new CheckedAnswers.Answered<>(true, FakeJudgmentModel.MODEL), answered);
        assertEquals(1L, counts.get("model_calls"));
        assertEquals(FakeJudgmentModel.INPUT_TOKENS, counts.get("input_tokens"));
        assertEquals(FakeJudgmentModel.OUTPUT_TOKENS, counts.get("output_tokens"));
    }

    @Test
    void asksOnceMoreWhenTheAnswerBreaksTheSchemaAndCountsBothCalls() {
        model.thenAnswers(BAD).thenAnswers(GOOD);

        CheckedAnswers.ask(model, REQUEST, CheckedAnswersTest::check, jobCounts);

        assertEquals(2, model.calls());
        assertEquals(2L, counts.get("model_calls"));
    }

    @Test
    void asksOnceMoreWhenTheAnswerCannotBeRead() {
        model.thenFails(new UnreadableAnswerException("cut off")).thenAnswers(GOOD);

        CheckedAnswers.ask(model, REQUEST, CheckedAnswersTest::check, jobCounts);

        assertEquals(2, model.calls());
    }

    @Test
    void refusesAfterTheSecondRefusal() {
        model.thenAnswers(BAD).thenAnswers(BAD).thenAnswers(GOOD);

        assertThrows(
                InvalidAnswerException.class,
                () -> CheckedAnswers.ask(model, REQUEST, CheckedAnswersTest::check, jobCounts));

        assertEquals(2, model.calls());
    }

    @Test
    void letsALimitReachedPassWithoutAskingAgain() {
        model.thenFails(new ModelLimitReachedException("every provider is at its limit"));

        assertThrows(
                ModelLimitReachedException.class,
                () -> CheckedAnswers.ask(model, REQUEST, CheckedAnswersTest::check, jobCounts));

        assertEquals(1, model.calls());
    }

    @Test
    void tellsTheModelWhyItsFirstAnswerWasRefusedBeforeAskingAgain() {
        model.thenAnswers(BAD).thenAnswers(GOOD);

        CheckedAnswers.ask(model, REQUEST, CheckedAnswersTest::check, jobCounts);

        assertEquals(List.of(REQUEST), model.refusedRequests());
        assertEquals(List.of("ok must be true"), model.refusalReasons());
    }
}
