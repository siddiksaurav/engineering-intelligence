package com.dsinnovators.ei.judgment;

/**
 * One OpenAI-compatible provider, from {@code ei.judgment.providers} (decision 158).
 *
 * @param name short name, stored in the run counts as {@code calls_<name>}
 * @param baseUrl the API root; {@code /chat/completions} is added to it
 * @param apiKey the key of the provider; never shown
 * @param model the model id sent with each request
 * @param reasoningEffort {@code low}, {@code medium} or {@code high}; null or blank when the provider has none
 */
public record JudgmentProvider(String name, String baseUrl, String apiKey, String model, String reasoningEffort) {

    @Override
    public String toString() {
        return "JudgmentProvider[name=" + name + ", baseUrl=" + baseUrl + ", apiKey=(set), model=" + model
                + ", reasoningEffort=" + reasoningEffort + "]";
    }

    boolean hasReasoningEffort() {
        return reasoningEffort != null && !reasoningEffort.isBlank();
    }
}
