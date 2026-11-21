package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class ReviewCommentTypesTest extends DatabaseTest {

    private static final Quarter QUARTER = Quarter.parse("2026-Q3");

    @Autowired
    private ReviewCommentTypes reviewCommentTypes;

    @Autowired
    private SettingService settings;

    @Autowired
    private PromptLibrary promptLibrary;

    @ParameterizedTest
    @CsvSource({
        "QUESTION,T1",
        "SMALL_CATCH,T1",
        "CORRECTNESS,T2",
        "MAINTAINABILITY,T2",
        "DESIGN,T3",
        "SECURITY,T3",
        "PERFORMANCE,T3",
        "ARCHITECTURE,T3"
    })
    void aSubstantiveTypeGivesItsTier(ReviewCommentAnswer.CommentType type, String tier) {
        ReviewCommentTypes.Classification classification = reviewCommentTypes.classify(type, QUARTER);

        assertTrue(classification.substantive());
        assertEquals(Optional.of(tier), classification.tier());
    }

    @ParameterizedTest
    @CsvSource({"STYLE", "ACKNOWLEDGEMENT"})
    void aTypeThatIsNotSubstantiveGivesNoTier(ReviewCommentAnswer.CommentType type) {
        ReviewCommentTypes.Classification classification = reviewCommentTypes.classify(type, QUARTER);

        assertFalse(classification.substantive());
        assertEquals(Optional.empty(), classification.tier());
    }

    @Test
    void aTypeMissingFromTheSettingIsRefusedWithItsName() {
        jdbc.update("update setting set value = value - 'design' where key = 'evidence.review_comment_types'");

        IllegalStateException refused = assertThrows(
                IllegalStateException.class,
                () -> reviewCommentTypes.classify(ReviewCommentAnswer.CommentType.DESIGN, QUARTER));

        assertTrue(refused.getMessage().contains("design"));
    }

    @Test
    void aSubstantiveTypeWithoutATierIsRefused() {
        jdbc.update(
                "update setting set value = jsonb_set(value, '{design}', '{\"substantive\": true, \"tier\": null}') where key = 'evidence.review_comment_types'");

        assertThrows(
                IllegalStateException.class,
                () -> reviewCommentTypes.classify(ReviewCommentAnswer.CommentType.DESIGN, QUARTER));
    }

    @Test
    void theSettingTheSchemaAndTheAnswerTypesHaveTheSameNames() {
        List<String> settingNames = settings.getJson("evidence.review_comment_types", QUARTER).propertyNames().stream()
                .sorted()
                .toList();
        JsonNode typeEnum = promptLibrary
                .schema("review-comments/v1")
                .path("properties")
                .path("comments")
                .path("items")
                .path("properties")
                .path("commentType")
                .path("enum");
        List<String> schemaNames =
                typeEnum.valueStream().map(JsonNode::asString).sorted().toList();
        List<String> answerNames = Arrays.stream(ReviewCommentAnswer.CommentType.values())
                .map(ReviewCommentAnswer.CommentType::wireName)
                .sorted()
                .toList();

        assertEquals(settingNames, schemaNames);
        assertEquals(settingNames, answerNames);
    }
}
