package com.dsinnovators.ei.judgment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Who made a judgment. The database holds the lower-case name. */
public enum JudgedBy {
    MODEL,
    PERSON;

    @Converter
    static class DatabaseConverter implements AttributeConverter<JudgedBy, String> {

        @Override
        public String convertToDatabaseColumn(JudgedBy judgedBy) {
            return judgedBy.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public JudgedBy convertToEntityAttribute(String column) {
            return valueOf(column.toUpperCase(Locale.ROOT));
        }
    }
}
