package com.dsinnovators.ei.judgment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The Stage 2 answer for the review comments of one call, read and checked against the comments that
 * were sent. Whether a comment is substantive and its tier are not part of it: they are read from the
 * setting {@code evidence.review_comment_types} (decision 170).
 */
record ReviewCommentAnswer(List<Comment> comments) {

    /** The ten comment types of the design, in the order the prompt lists them. */
    enum CommentType implements WireNamed {
        QUESTION,
        SMALL_CATCH,
        CORRECTNESS,
        MAINTAINABILITY,
        DESIGN,
        SECURITY,
        PERFORMANCE,
        ARCHITECTURE,
        STYLE,
        ACKNOWLEDGEMENT
    }

    /**
     * The judgment of one comment.
     *
     * @param commentId the key the comment had in the input, such as C-1234
     */
    record Comment(String commentId, CommentType commentType, String reason) {}

    /**
     * Reads and checks the model's answer.
     *
     * @param sentCommentIds the keys of the comments in the input; each must be named exactly once
     * @throws InvalidAnswerException naming the field and the value when the answer breaks the schema
     */
    static ReviewCommentAnswer fromAnswer(JsonNode answer, Collection<String> sentCommentIds) {
        if (!answer.isObject()) {
            throw new InvalidAnswerException("The answer must be a JSON object, was " + answer.getNodeType());
        }
        JsonNode node = AnswerFields.required(answer, "comments");
        if (!node.isArray()) {
            throw new InvalidAnswerException("The field comments must be a list, was " + node);
        }
        List<Comment> comments = new ArrayList<>();
        for (JsonNode comment : node.values()) {
            comments.add(new Comment(
                    AnswerFields.textOf(comment, "commentId"),
                    AnswerFields.enumOf(CommentType.class, comment, "commentType"),
                    AnswerFields.textOf(comment, "reason")));
        }
        requireEveryCommentOnce(comments, sentCommentIds);
        return new ReviewCommentAnswer(List.copyOf(comments));
    }

    private static void requireEveryCommentOnce(List<Comment> comments, Collection<String> sentCommentIds) {
        Set<String> sent = Set.copyOf(sentCommentIds);
        Set<String> named = new HashSet<>();
        for (Comment comment : comments) {
            if (!sent.contains(comment.commentId())) {
                throw new InvalidAnswerException(
                        "The answer names the comment " + comment.commentId() + ", which was not sent");
            }
            if (!named.add(comment.commentId())) {
                throw new InvalidAnswerException("The answer names the comment " + comment.commentId() + " twice");
            }
        }
        List<String> missing =
                sentCommentIds.stream().filter(id -> !named.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidAnswerException("The answer leaves out the comments " + missing);
        }
    }
}
