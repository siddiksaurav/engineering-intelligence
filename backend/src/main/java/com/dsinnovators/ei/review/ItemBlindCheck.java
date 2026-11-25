package com.dsinnovators.ei.review;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * What the blind check holds about one sample item: the user's label and the user's mark of the AI's
 * reason. Each is null while the user has not given it.
 */
public record ItemBlindCheck(Label label, Mark reasonMark) {

    /** The user's label; {@code complexity} is null when the user could not judge the item. */
    public record Label(String complexity, boolean cannotJudge, String note, Instant labeledAt) {}

    /** The user's verdict on the AI's reason; {@code problemMarks} is {@code [{name, verdict}]}. */
    public record Mark(boolean reasonRight, JsonNode problemMarks, String note, Instant markedAt) {}
}
