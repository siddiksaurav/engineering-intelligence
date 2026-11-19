package com.dsinnovators.ei.job;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter
class JobRunStatusConverter implements AttributeConverter<JobRunStatus, String> {

    @Override
    public String convertToDatabaseColumn(JobRunStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public JobRunStatus convertToEntityAttribute(String column) {
        return JobRunStatus.valueOf(column.toUpperCase(Locale.ROOT));
    }
}
