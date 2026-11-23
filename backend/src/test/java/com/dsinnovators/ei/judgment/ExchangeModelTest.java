package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class ExchangeModelTest extends DatabaseTest {

    private static final ModelRequest REQUEST = new ModelRequest(
            "pull-request-digest/v1", "Digest the PR", "PR #1", "digest", CanonicalJson.parse("{\"type\":\"object\"}"));
    private static final JsonNode ANSWER = CanonicalJson.parse("{\"workType\":\"fix\"}");
    private static final String ANSWERED_BY = "claude-sonnet-5-5 via claude-code";

    @Autowired
    private ModelExchange exchange;

    private ExchangeModel model() {
        return new ExchangeModel(exchange);
    }

    @Test
    void aRequestWithoutAnAnswerIsLeftWaitingForTheExchange() {
        AnswerNotYetGivenException waiting =
                assertThrows(AnswerNotYetGivenException.class, () -> model().judge(REQUEST));

        String hash = ExchangeModel.hashOf(REQUEST);
        assertTrue(waiting.getMessage().contains(hash), waiting.getMessage());
        assertEquals(
                hash, exchange.waitingRequests(Optional.empty(), 10).getFirst().requestHash());
    }

    @Test
    void aGivenAnswerIsReturnedAsTheModelsAnswerWithItsAuthorAsTheModel() {
        exchange.await(ExchangeModel.hashOf(REQUEST), REQUEST);
        exchange.giveAnswer(ExchangeModel.hashOf(REQUEST), ANSWER, ANSWERED_BY);

        ModelAnswer answer = model().judge(REQUEST);

        assertEquals(new ModelAnswer(ANSWER, ANSWERED_BY, 0, 0), answer);
    }

    @Test
    void aRefusedAnswerSendsTheRequestBackToTheExchange() {
        exchange.await(ExchangeModel.hashOf(REQUEST), REQUEST);
        exchange.giveAnswer(ExchangeModel.hashOf(REQUEST), ANSWER, ANSWERED_BY);

        model().answerRefused(REQUEST, "facts are missing");

        assertThrows(AnswerNotYetGivenException.class, () -> model().judge(REQUEST));
        assertEquals(
                "facts are missing",
                exchange.waitingRequests(Optional.empty(), 10).getFirst().refusalReason());
    }

    @Test
    void theHashChangesWithEveryPartOfTheRequest() {
        ModelRequest otherInput = new ModelRequest(
                REQUEST.promptVersion(), REQUEST.instructions(), "PR #2", REQUEST.schemaName(), REQUEST.jsonSchema());
        ModelRequest otherPrompt = new ModelRequest(
                "pull-request-digest/v2",
                REQUEST.instructions(),
                REQUEST.input(),
                REQUEST.schemaName(),
                REQUEST.jsonSchema());

        assertEquals(ExchangeModel.hashOf(REQUEST), ExchangeModel.hashOf(REQUEST));
        assertNotEquals(ExchangeModel.hashOf(REQUEST), ExchangeModel.hashOf(otherInput));
        assertNotEquals(ExchangeModel.hashOf(REQUEST), ExchangeModel.hashOf(otherPrompt));
    }
}
