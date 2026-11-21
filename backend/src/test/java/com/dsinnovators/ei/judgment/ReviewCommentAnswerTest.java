package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ReviewCommentAnswerTest {

    private static final List<String> SENT = List.of("C-11", "C-12");

    private static JsonNode answerOf(String comments) {
        return CanonicalJson.parse("{\"comments\": [" + comments + "]}");
    }

    private static String comment(String id, String type, String reason) {
        return "{\"commentId\": \"" + id + "\", \"commentType\": \"" + type + "\", \"reason\": \"" + reason + "\"}";
    }

    private static ReviewCommentAnswer read(JsonNode answer) {
        return ReviewCommentAnswer.fromAnswer(answer, SENT);
    }

    @Test
    void refusesAnAnswerThatLeavesOutAComment() {
        JsonNode answer = answerOf(comment("C-11", "question", "Asks why."));

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("C-12"));
    }

    @Test
    void readsAnAnswerThatNamesEveryCommentOnceInAnyOrder() {
        ReviewCommentAnswer answer = read(answerOf(comment("C-12", "design", "Moves the logic to another class.") + ","
                + comment("C-11", "style", "A matter of taste.")));

        assertEquals(2, answer.comments().size());
        assertEquals(
                new ReviewCommentAnswer.Comment(
                        "C-12", ReviewCommentAnswer.CommentType.DESIGN, "Moves the logic to another class."),
                answer.comments().get(0));
        assertEquals(
                ReviewCommentAnswer.CommentType.STYLE, answer.comments().get(1).commentType());
    }

    @Test
    void refusesAnAnswerThatNamesACommentTwice() {
        JsonNode answer = answerOf(comment("C-11", "question", "a") + "," + comment("C-11", "question", "b") + ","
                + comment("C-12", "style", "c"));

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("C-11"));
    }

    @Test
    void refusesAnAnswerThatNamesAnUnknownComment() {
        JsonNode answer = answerOf(comment("C-11", "question", "a") + "," + comment("C-12", "style", "b") + ","
                + comment("C-99", "style", "c"));

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("C-99"));
    }

    @Test
    void refusesACommentTypeOutsideTheTen() {
        JsonNode answer = answerOf(comment("C-11", "nitpick", "a") + "," + comment("C-12", "style", "b"));

        InvalidAnswerException refused = assertThrows(InvalidAnswerException.class, () -> read(answer));

        assertTrue(refused.getMessage().contains("commentType"));
        assertTrue(refused.getMessage().contains("nitpick"));
    }

    @Test
    void refusesABlankReason() {
        JsonNode answer = answerOf(comment("C-11", "question", " ") + "," + comment("C-12", "style", "b"));

        assertThrows(InvalidAnswerException.class, () -> read(answer));
    }

    @Test
    void refusesAnAnswerWithoutTheCommentList() {
        assertThrows(InvalidAnswerException.class, () -> read(CanonicalJson.parse("{}")));
        assertThrows(InvalidAnswerException.class, () -> read(CanonicalJson.parse("{\"comments\": \"none\"}")));
        assertThrows(InvalidAnswerException.class, () -> read(CanonicalJson.parse("[]")));
    }

    @Test
    void theTenTypesAreTheOnesOfTheDesign() {
        assertEquals(
                List.of(
                        "question",
                        "small_catch",
                        "correctness",
                        "maintainability",
                        "design",
                        "security",
                        "performance",
                        "architecture",
                        "style",
                        "acknowledgement"),
                Arrays.stream(ReviewCommentAnswer.CommentType.values())
                        .map(ReviewCommentAnswer.CommentType::wireName)
                        .toList());
    }
}
