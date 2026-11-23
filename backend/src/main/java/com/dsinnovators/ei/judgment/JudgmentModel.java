package com.dsinnovators.ei.judgment;

/**
 * The only way to reach the model. Only the {@code judgment} package uses it; the rest of the
 * system reads the judgments it stored.
 */
public interface JudgmentModel {

    /**
     * Sends one request to the model and returns its structured answer.
     *
     * @throws ModelCallException when the call fails or the answer cannot be read
     * @throws ModelLimitReachedException when no provider can take the request any more
     */
    ModelAnswer judge(ModelRequest request);

    /**
     * Learns that the answer to the request broke its schema or could not be read; the request is
     * asked once more right after. A provider that keeps no answers ignores it.
     */
    default void answerRefused(ModelRequest request, String reason) {}
}
