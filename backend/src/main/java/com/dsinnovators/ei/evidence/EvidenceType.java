package com.dsinnovators.ei.evidence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** The kinds of evidence item F05 builds. The database holds the lower-case name. */
public enum EvidenceType {
    FEATURE_WORK,
    BUG_FIX,
    REVIEW_COMMENT_WRITTEN;

    /** The name stored in {@code evidence_item.evidence_type}. */
    public String databaseName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Converter
    static class DatabaseConverter implements AttributeConverter<EvidenceType, String> {

        @Override
        public String convertToDatabaseColumn(EvidenceType type) {
            return type.databaseName();
        }

        @Override
        public EvidenceType convertToEntityAttribute(String column) {
            return valueOf(column.toUpperCase(Locale.ROOT));
        }
    }
}
