package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the {@code dimension_score} rows of the newest run that scored a quarter (decisions 213, 217, 229). */
@Component
public class DimensionScoreReader {

    // A run for another quarter, a failed run and a run that wrote nothing for the quarter never hide an older run.
    private static final String NEWEST_RUN = """
            select run.id
            from job_run run
            where run.job_name = 'dimension-scoring'
              and run.status = 'succeeded'
              and exists (select 1 from dimension_score score
                          where score.job_run_id = run.id and score.quarter = :quarter)
            order by run.id desc
            limit 1
            """;

    private static final String SCORES = """
            select dimension.key, dimension.name, score.working_tier, score.score,
                   score.gates::text as gates, score.trail::text as trail, score.calculated_at
            from dimension_score score
            join dimension on dimension.id = score.dimension_id
            where score.job_run_id = :runId and score.person_id = :personId and score.selected_level = :level
            order by dimension.id
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    DimensionScoreReader(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /**
     * The scores of the person at the level from the newest succeeded run of {@code dimension-scoring}
     * that wrote rows for the quarter. Empty ({@link StoredDimensionScores#scored()} false) when no
     * such run exists, and when the run exists but did not score the person, because the person had no
     * work in the quarter.
     */
    @Transactional(readOnly = true)
    public StoredDimensionScores of(long personId, Quarter quarter, Level level) {
        Optional<Long> runId = jdbc.sql(NEWEST_RUN)
                .param("quarter", quarter.toString())
                .query(Long.class)
                .optional();
        if (runId.isEmpty()) {
            return StoredDimensionScores.nothing();
        }
        List<OffsetDateTime> calculatedAt = new ArrayList<>();
        List<StoredDimensionScore> dimensions = jdbc.sql(SCORES)
                .param("runId", runId.get())
                .param("personId", personId)
                .param("level", level.name())
                .query((row, rowNumber) -> {
                    calculatedAt.add(row.getObject("calculated_at", OffsetDateTime.class));
                    return scoreOf(
                            row.getString("key"),
                            row.getString("name"),
                            row.getString("working_tier"),
                            row.getInt("score"),
                            jsonMapper.readTree(row.getString("gates")),
                            jsonMapper.readTree(row.getString("trail")));
                })
                .list();
        if (dimensions.isEmpty()) {
            return StoredDimensionScores.nothing();
        }
        return new StoredDimensionScores(runId.get(), calculatedAt.getFirst().toInstant(), dimensions);
    }

    private static StoredDimensionScore scoreOf(
            String key, String name, String workingTier, int score, JsonNode gates, JsonNode trail) {
        List<String> notes = new ArrayList<>();
        trail.path("notes").forEach(note -> notes.add(note.stringValue()));
        return new StoredDimensionScore(
                key, name, workingTier == null ? null : Tier.fromDatabase(workingTier), score, gates, notes, trail);
    }
}
