package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.CanonicalJson;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The model, answered through the model exchange by Claude Code instead of an HTTP provider (PoC
 * only, decision 271). A request with no answer yet is put into the exchange and the subject is left
 * for later; the next run of the job finds the answer and goes the normal way, checks included.
 */
final class ExchangeModel implements JudgmentModel {

    private final ModelExchange exchange;

    ExchangeModel(ModelExchange exchange) {
        this.exchange = exchange;
    }

    /** SHA-256 over every part of the request, so a changed prompt or input is a new request. */
    static String hashOf(ModelRequest request) {
        ObjectNode hashed = JsonNodeFactory.instance.objectNode();
        hashed.put("promptVersion", request.promptVersion());
        hashed.put("instructions", request.instructions());
        hashed.put("input", request.input());
        hashed.put("schemaName", request.schemaName());
        hashed.set("jsonSchema", request.jsonSchema());
        return CanonicalJson.sha256Hex(hashed);
    }

    /** @throws AnswerNotYetGivenException when the exchange holds no answer yet; the request then waits in it */
    @Override
    public ModelAnswer judge(ModelRequest request) {
        String hash = hashOf(request);
        return exchange.answerOf(hash)
                .map(given -> new ModelAnswer(given.answer(), given.answeredBy(), 0, 0))
                .orElseThrow(() -> {
                    exchange.await(hash, request);
                    return new AnswerNotYetGivenException("Request " + hash + " (" + request.promptVersion()
                            + ") waits for its answer in the model exchange");
                });
    }

    @Override
    public void answerRefused(ModelRequest request, String reason) {
        exchange.reopen(hashOf(request), reason);
    }
}
