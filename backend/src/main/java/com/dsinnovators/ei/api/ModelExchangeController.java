package com.dsinnovators.ei.api;

import com.dsinnovators.ei.judgment.ExchangeRequest;
import com.dsinnovators.ei.judgment.ModelExchange;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * The model exchange, for Claude Code to read the waiting model requests and give their answers (PoC
 * only, decision 271). Reached only from the machine the backend runs on.
 */
@RestController
@RequestMapping("/api/model-exchange/requests")
class ModelExchangeController {

    private static final int MAX_LIMIT = 100;

    private final ModelExchange exchange;

    ModelExchangeController(ModelExchange exchange) {
        this.exchange = exchange;
    }

    /** The answer to one request and the model that gave it. */
    record AnswerRequest(JsonNode answer, String answeredBy) {}

    /**
     * @param promptVersion only the requests of this prompt version, such as {@code pull-request-digest/v1}
     * @param limit 1 to 100
     */
    @GetMapping
    List<ExchangeRequest> waiting(
            @RequestParam Optional<String> promptVersion, @RequestParam(defaultValue = "10") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidExchangeLimitException("limit must be 1 to " + MAX_LIMIT + ", was " + limit);
        }
        return exchange.waitingRequests(promptVersion.filter(version -> !version.isBlank()), limit);
    }

    @PutMapping("/{requestHash}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answer(@PathVariable String requestHash, @RequestBody AnswerRequest body) {
        exchange.giveAnswer(requestHash, body.answer(), body.answeredBy());
    }
}
