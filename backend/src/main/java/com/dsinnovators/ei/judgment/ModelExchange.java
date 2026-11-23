package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The {@code model_exchange} table: model requests that wait for Claude Code to answer them, and the
 * answers it gave. PoC only (decision 271); the jobs still check every answer and store the judgments.
 */
@Component
public class ModelExchange {

    /** An answer given to a request, with who gave it; the author is stored as the judgment's model. */
    record GivenAnswer(JsonNode answer, String answeredBy) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    ModelExchange(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * The requests that wait for an answer, oldest first.
     *
     * @param promptVersion only the requests of this prompt version; all when empty
     * @param limit at most this many
     */
    public List<ExchangeRequest> waitingRequests(Optional<String> promptVersion, int limit) {
        return jdbc.sql("""
                        select request_hash, prompt_version, instructions, input, schema_name,
                               json_schema::text as json_schema, refusal_reason
                        from model_exchange
                        where status = 'waiting' and (cast(:promptVersion as text) is null or prompt_version = :promptVersion)
                        order by asked_at, request_hash
                        limit :limit
                        """)
                .param("promptVersion", promptVersion.orElse(null))
                .param("limit", limit)
                .query((row, rowNumber) -> new ExchangeRequest(
                        row.getString("request_hash"),
                        row.getString("prompt_version"),
                        row.getString("instructions"),
                        row.getString("input"),
                        row.getString("schema_name"),
                        CanonicalJson.parse(row.getString("json_schema")),
                        row.getString("refusal_reason")))
                .list();
    }

    /**
     * Gives the answer to a waiting request.
     *
     * @throws InvalidExchangeAnswerException when the answer is not a JSON object or the author is blank
     * @throws ExchangeRequestNotFoundException when no request has the hash
     * @throws ExchangeRequestAlreadyAnsweredException when the request has an answer already
     */
    public void giveAnswer(String requestHash, JsonNode answer, String answeredBy) {
        if (answer == null || !answer.isObject()) {
            throw new InvalidExchangeAnswerException(
                    "The answer to request " + requestHash + " must be a JSON object, was " + answer);
        }
        if (answeredBy == null || answeredBy.isBlank()) {
            throw new InvalidExchangeAnswerException(
                    "answeredBy must name the model that gave the answer to request " + requestHash);
        }
        int answered = jdbc.sql("""
                        update model_exchange
                        set status = 'answered', answer = cast(:answer as jsonb), answered_by = :answeredBy,
                            answered_at = :answeredAt
                        where request_hash = :requestHash and status = 'waiting'
                        """)
                .param("answer", CanonicalJson.canonicalize(answer))
                .param("answeredBy", answeredBy.strip())
                .param("answeredAt", now())
                .param("requestHash", requestHash)
                .update();
        if (answered == 0) {
            throw statusOf(requestHash).isEmpty()
                    ? new ExchangeRequestNotFoundException("No model exchange request has the hash " + requestHash)
                    : new ExchangeRequestAlreadyAnsweredException(
                            "The model exchange request " + requestHash + " has an answer already");
        }
    }

    /** Puts the request into the exchange to wait for an answer, unless it is there already. */
    void await(String requestHash, ModelRequest request) {
        jdbc.sql("""
                        insert into model_exchange
                            (request_hash, prompt_version, schema_name, instructions, input, json_schema, status, asked_at)
                        values (:requestHash, :promptVersion, :schemaName, :instructions, :input,
                                cast(:jsonSchema as jsonb), 'waiting', :askedAt)
                        on conflict (request_hash) do nothing
                        """)
                .param("requestHash", requestHash)
                .param("promptVersion", request.promptVersion())
                .param("schemaName", request.schemaName())
                .param("instructions", request.instructions())
                .param("input", request.input())
                .param("jsonSchema", CanonicalJson.canonicalize(request.jsonSchema()))
                .param("askedAt", now())
                .update();
    }

    /** The answer given to the request; empty while it waits or when no request has the hash. */
    Optional<GivenAnswer> answerOf(String requestHash) {
        return jdbc.sql("""
                        select answer::text as answer, answered_by from model_exchange
                        where request_hash = :requestHash and status = 'answered'
                        """)
                .param("requestHash", requestHash)
                .query((row, rowNumber) ->
                        new GivenAnswer(CanonicalJson.parse(row.getString("answer")), row.getString("answered_by")))
                .optional();
    }

    /** Drops the answer of the request, which then waits again, with the reason the answer was refused. */
    void reopen(String requestHash, String refusalReason) {
        jdbc.sql("""
                        update model_exchange
                        set status = 'waiting', answer = null, answered_by = null, answered_at = null,
                            refusal_reason = :refusalReason
                        where request_hash = :requestHash
                        """)
                .param("refusalReason", refusalReason)
                .param("requestHash", requestHash)
                .update();
    }

    private Optional<String> statusOf(String requestHash) {
        return jdbc.sql("select status from model_exchange where request_hash = :requestHash")
                .param("requestHash", requestHash)
                .query(String.class)
                .optional();
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }
}
