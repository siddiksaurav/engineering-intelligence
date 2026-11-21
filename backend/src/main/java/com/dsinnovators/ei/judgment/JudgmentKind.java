package com.dsinnovators.ei.judgment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** What a judgment is about and what it answers. The database holds the lower-case name. */
public enum JudgmentKind {
    /** Stage 1: the digest of one pull request record version. */
    PULL_REQUEST_DIGEST(JudgmentSubject.Type.PULL_REQUEST_RECORD),
    /** The tier of one Test change, Refactor and performance, Security or Infra and release item row. */
    ITEM_TIER(JudgmentSubject.Type.EVIDENCE_ITEM),
    /** Stage 2: the complexity of one Feature work item row, with its rubric facts. */
    FEATURE_WORK_COMPLEXITY(JudgmentSubject.Type.EVIDENCE_ITEM),
    /** Stage 2: the type of one Review comment written item row. */
    REVIEW_COMMENT(JudgmentSubject.Type.EVIDENCE_ITEM);

    private final JudgmentSubject.Type subjectType;

    JudgmentKind(JudgmentSubject.Type subjectType) {
        this.subjectType = subjectType;
    }

    /** The kind of thing a judgment of this kind is made about. */
    JudgmentSubject.Type subjectType() {
        return subjectType;
    }

    /** The name stored in the database and put into input hashes. */
    public String databaseName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Converter
    static class DatabaseConverter implements AttributeConverter<JudgmentKind, String> {

        @Override
        public String convertToDatabaseColumn(JudgmentKind kind) {
            return kind.databaseName();
        }

        @Override
        public JudgmentKind convertToEntityAttribute(String column) {
            return valueOf(column.toUpperCase(Locale.ROOT));
        }
    }
}
