package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class ModelExchangeTest extends DatabaseTest {

    private static final ModelRequest DIGEST_REQUEST = new ModelRequest(
            "pull-request-digest/v1", "Digest the PR", "PR #1", "digest", CanonicalJson.parse("{\"type\":\"object\"}"));
    private static final ModelRequest REVIEW_REQUEST = new ModelRequest(
            "review-comments/v1",
            "Type the comments",
            "Comment 7",
            "review",
            CanonicalJson.parse("{\"type\":\"object\"}"));
    private static final JsonNode ANSWER = CanonicalJson.parse("{\"workType\":\"fix\"}");
    private static final String ANSWERED_BY = "claude-sonnet-5-5 via claude-code";

    @Autowired
    private ModelExchange exchange;

    @Test
    void aRequestWaitsForItsAnswerOnceHoweverOftenItIsAskedFor() {
        exchange.await("hash-1", DIGEST_REQUEST);
        exchange.await("hash-1", DIGEST_REQUEST);

        List<ExchangeRequest> waiting = exchange.waitingRequests(Optional.empty(), 10);

        assertEquals(1, waiting.size());
        ExchangeRequest request = waiting.getFirst();
        assertEquals("hash-1", request.requestHash());
        assertEquals("pull-request-digest/v1", request.promptVersion());
        assertEquals("Digest the PR", request.instructions());
        assertEquals("PR #1", request.input());
        assertEquals("digest", request.schemaName());
        assertEquals(DIGEST_REQUEST.jsonSchema(), request.jsonSchema());
        assertEquals(null, request.refusalReason());
    }

    @Test
    void waitingRequestsAreFilteredByPromptVersionAndLimited() {
        exchange.await("hash-1", DIGEST_REQUEST);
        exchange.await("hash-2", REVIEW_REQUEST);
        exchange.await("hash-3", DIGEST_REQUEST);

        assertEquals(
                List.of("hash-2"),
                exchange.waitingRequests(Optional.of("review-comments/v1"), 10).stream()
                        .map(ExchangeRequest::requestHash)
                        .toList());
        assertEquals(1, exchange.waitingRequests(Optional.empty(), 1).size());
    }

    @Test
    void aGivenAnswerIsFoundAndNoLongerWaits() {
        exchange.await("hash-1", DIGEST_REQUEST);

        exchange.giveAnswer("hash-1", ANSWER, ANSWERED_BY);

        assertEquals(Optional.of(new ModelExchange.GivenAnswer(ANSWER, ANSWERED_BY)), exchange.answerOf("hash-1"));
        assertTrue(exchange.waitingRequests(Optional.empty(), 10).isEmpty());
    }

    @Test
    void aRequestWithoutAnAnswerHasNone() {
        exchange.await("hash-1", DIGEST_REQUEST);

        assertEquals(Optional.empty(), exchange.answerOf("hash-1"));
        assertEquals(Optional.empty(), exchange.answerOf("unknown"));
    }

    @Test
    void aRefusedAnswerIsDroppedAndTheRequestWaitsAgainWithTheReason() {
        exchange.await("hash-1", DIGEST_REQUEST);
        exchange.giveAnswer("hash-1", ANSWER, ANSWERED_BY);

        exchange.reopen("hash-1", "facts are missing");

        assertEquals(Optional.empty(), exchange.answerOf("hash-1"));
        ExchangeRequest waiting = exchange.waitingRequests(Optional.empty(), 10).getFirst();
        assertEquals("facts are missing", waiting.refusalReason());
    }

    @Test
    void anAnswerToAnUnknownRequestIsRefused() {
        ExchangeRequestNotFoundException refused = assertThrows(
                ExchangeRequestNotFoundException.class, () -> exchange.giveAnswer("unknown", ANSWER, ANSWERED_BY));

        assertTrue(refused.getMessage().contains("unknown"), refused.getMessage());
    }

    @Test
    void aSecondAnswerToTheSameRequestIsRefused() {
        exchange.await("hash-1", DIGEST_REQUEST);
        exchange.giveAnswer("hash-1", ANSWER, ANSWERED_BY);

        assertThrows(
                ExchangeRequestAlreadyAnsweredException.class,
                () -> exchange.giveAnswer("hash-1", ANSWER, ANSWERED_BY));
    }

    @Test
    void anAnswerThatIsNoJsonObjectOrHasNoAuthorIsRefused() {
        exchange.await("hash-1", DIGEST_REQUEST);

        assertThrows(
                InvalidExchangeAnswerException.class,
                () -> exchange.giveAnswer("hash-1", CanonicalJson.parse("[1]"), ANSWERED_BY));
        assertThrows(InvalidExchangeAnswerException.class, () -> exchange.giveAnswer("hash-1", ANSWER, " "));
    }
}
