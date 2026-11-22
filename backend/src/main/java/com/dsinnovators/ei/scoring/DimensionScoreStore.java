package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code dimension_score} rows. Rows are only inserted, never updated: a new run inserts new
 * rows (decision 213). Joins the transaction of the caller, so a run writes all rows or none.
 */
@Component
class DimensionScoreStore {

    private static final String INSERT = """
            insert into dimension_score
                (job_run_id, person_id, dimension_id, quarter, selected_level, working_tier, score, gates, trail,
                 calculated_at)
            values (:jobRunId, :personId, :dimensionId, :quarter, :level, :workingTier, :score,
                    cast(:gates as jsonb), cast(:trail as jsonb), :calculatedAt)
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    DimensionScoreStore(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /** Inserts the scores of each person, with the run that made them; returns the number of rows written. */
    int insert(
            long jobRunId,
            Quarter quarter,
            Instant calculatedAt,
            Map<Long, List<DimensionScoreResult>> resultsByPerson) {
        OffsetDateTime calculatedAtUtc = calculatedAt.atOffset(ZoneOffset.UTC);
        int written = 0;
        for (Map.Entry<Long, List<DimensionScoreResult>> person : resultsByPerson.entrySet()) {
            for (DimensionScoreResult result : person.getValue()) {
                written += jdbc.sql(INSERT)
                        .param("jobRunId", jobRunId)
                        .param("personId", person.getKey())
                        .param("dimensionId", result.dimensionId())
                        .param("quarter", quarter.toString())
                        .param("level", result.level().name())
                        .param(
                                "workingTier",
                                result.workingTier() == null
                                        ? null
                                        : result.workingTier().name(),
                                Types.VARCHAR)
                        .param("score", result.score())
                        .param("gates", jsonMapper.writeValueAsString(result.gates()))
                        .param("trail", jsonMapper.writeValueAsString(result.trail()))
                        .param("calculatedAt", calculatedAtUtc)
                        .update();
            }
        }
        return written;
    }
}
