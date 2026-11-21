package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JudgmentKindTest {

    @Test
    void theDatabaseNameIsTheLowerCaseNameThatTheHashesUse() {
        assertEquals("pull_request_digest", JudgmentKind.PULL_REQUEST_DIGEST.databaseName());
        assertEquals("item_tier", JudgmentKind.ITEM_TIER.databaseName());
    }

    @Test
    void theConverterReadsWhatItWrites() {
        JudgmentKind.DatabaseConverter converter = new JudgmentKind.DatabaseConverter();

        for (JudgmentKind kind : JudgmentKind.values()) {
            assertEquals(kind, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(kind)));
        }
    }
}
