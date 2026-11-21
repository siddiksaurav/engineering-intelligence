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
}
