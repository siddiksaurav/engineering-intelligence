package com.dsinnovators.ei.job;

import com.dsinnovators.ei.common.CanonicalJson;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** One run of a job: what was started, by whom, and how it ended. */
@Entity
@Table(name = "job_run")
class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;

    // JSON columns are held as text and converted here (decision 28).
    @JdbcTypeCode(SqlTypes.JSON)
    private String arguments;

    private String startedBy;

    @Convert(converter = JobRunStatusConverter.class)
    private JobRunStatus status;

    private Instant startedAt;

    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String counts;

    private String errorMessage;

    protected JobRun() {}

    JobRun(String jobName, Map<String, String> arguments, String startedBy, Instant startedAt) {
        this.jobName = jobName;
        this.arguments = toJson(arguments);
        this.startedBy = startedBy;
        this.status = JobRunStatus.RUNNING;
        this.startedAt = startedAt;
        this.counts = toJson(Map.of());
    }

    Long getId() {
        return id;
    }

    String getJobName() {
        return jobName;
    }

    Map<String, String> getArguments() {
        Map<String, String> values = new TreeMap<>();
        CanonicalJson.parse(arguments)
                .properties()
                .forEach(entry -> values.put(entry.getKey(), entry.getValue().stringValue()));
        return values;
    }

    String getStartedBy() {
        return startedBy;
    }

    JobRunStatus getStatus() {
        return status;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Optional<Instant> getFinishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    Map<String, Long> getCounts() {
        Map<String, Long> values = new TreeMap<>();
        CanonicalJson.parse(counts)
                .properties()
                .forEach(entry -> values.put(entry.getKey(), entry.getValue().longValue()));
        return values;
    }

    Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    private static String toJson(Map<String, ?> values) {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        values.forEach((key, value) -> object.set(key, value(value)));
        return CanonicalJson.canonicalize(object);
    }

    private static JsonNode value(Object value) {
        return switch (value) {
            case String text -> JsonNodeFactory.instance.stringNode(text);
            case Long number -> JsonNodeFactory.instance.numberNode(number);
            default -> throw new IllegalArgumentException("Not a text or a count: " + value);
        };
    }
}
