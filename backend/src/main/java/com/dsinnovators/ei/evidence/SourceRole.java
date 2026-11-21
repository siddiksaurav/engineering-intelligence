package com.dsinnovators.ei.evidence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** What a source record is to the item it was used for. The database holds the lower-case name. */
public enum SourceRole {
    PULL_REQUEST,
    WORK_PACKAGE,
    REVIEW_COMMENT;

    @Converter
    static class DatabaseConverter implements AttributeConverter<SourceRole, String> {

        @Override
        public String convertToDatabaseColumn(SourceRole role) {
            return role.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public SourceRole convertToEntityAttribute(String column) {
            return valueOf(column.toUpperCase(Locale.ROOT));
        }
    }
}
