package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JudgmentKindTest {

    @Test
    void theDatabaseNameIsTheLowerCaseNameThatTheHashesUse() {
        assertEquals("pull_request_digest", JudgmentKind.PULL_REQUEST_DIGEST.databaseName());
        assertEquals("item_tier", JudgmentKind.ITEM_TIER.databaseName());
        assertEquals("feature_work_complexity", JudgmentKind.FEATURE_WORK_COMPLEXITY.databaseName());
        assertEquals("review_comment", JudgmentKind.REVIEW_COMMENT.databaseName());
    }

    @Test
    void theConverterReadsWhatItWrites() {
        JudgmentKind.DatabaseConverter converter = new JudgmentKind.DatabaseConverter();

        for (JudgmentKind kind : JudgmentKind.values()) {
            assertEquals(kind, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(kind)));
        }
    }

    @Test
    void theStage2KindsAreAboutAnEvidenceItem() {
        assertEquals(JudgmentSubject.Type.EVIDENCE_ITEM, JudgmentKind.FEATURE_WORK_COMPLEXITY.subjectType());
        assertEquals(JudgmentSubject.Type.EVIDENCE_ITEM, JudgmentKind.REVIEW_COMMENT.subjectType());
    }
}
