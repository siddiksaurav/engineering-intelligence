package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reads from the setting {@code evidence.review_comment_types} whether a comment type is substantive
 * and which tier it gives (decision 170). The model gives only the type. Used by the review comment
 * judge, a later task of F07.
 */
@Component
class ReviewCommentTypes {

    private static final String SETTING = "evidence.review_comment_types";

    /**
     * What a comment type means for the score.
     *
     * @param tier T1 to T3; empty for a type that is not substantive
     */
    record Classification(boolean substantive, Optional<String> tier) {}

    private final SettingService settings;

    ReviewCommentTypes(SettingService settings) {
        this.settings = settings;
    }

    /**
     * Reads the classification of a type in a quarter.
     *
     * @throws IllegalStateException when the setting has no entry for the type, or no tier for a
     *     substantive one
     */
    Classification classify(ReviewCommentAnswer.CommentType type, Quarter quarter) {
        JsonNode entry = settings.getJson(SETTING, quarter).path(type.wireName());
        if (!entry.path("substantive").isBoolean()) {
            throw new IllegalStateException(
                    "The setting " + SETTING + " has no entry for the comment type " + type.wireName());
        }
        boolean substantive = entry.path("substantive").booleanValue();
        JsonNode tier = entry.path("tier");
        if (substantive && !tier.isString()) {
            throw new IllegalStateException(
                    "The setting " + SETTING + " gives no tier to the substantive comment type " + type.wireName());
        }
        return new Classification(substantive, substantive ? Optional.of(tier.asString()) : Optional.empty());
    }
}
