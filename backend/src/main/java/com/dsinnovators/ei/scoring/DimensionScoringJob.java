package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.job.Job;
import com.dsinnovators.ei.job.JobArguments;
import com.dsinnovators.ei.job.JobCounts;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scores every person of a quarter in both dimensions at levels L1, L2 and L3 and inserts the rows
 * with the id of its run (F11 design, decisions 205, 213, 216). The reads and the inserts share one
 * repeatable-read transaction: one consistent view of the data, and all rows of a run or none.
 * Run it after {@code feature-work-judgment} and {@code review-comment-judgment}.
 */
@Component
class DimensionScoringJob implements Job {

    private static final String NAME = "dimension-scoring";
    private static final String QUARTER = "quarter";
    private static final String RUNNING_RUN_ID =
            "select id from job_run where job_name = :jobName and status = 'running'";

    private final ScoringSettingsReader settingsReader;
    private final DimensionMappings dimensionMappings;
    private final ScoredItems scoredItems;
    private final GateNumbersQuery gateNumbersQuery;
    private final DimensionScoreStore store;
    private final JdbcClient jdbc;
    private final TransactionTemplate snapshot;
    private final Clock clock;

    DimensionScoringJob(
            ScoringSettingsReader settingsReader,
            DimensionMappings dimensionMappings,
            ScoredItems scoredItems,
            GateNumbersQuery gateNumbersQuery,
            DimensionScoreStore store,
            JdbcClient jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.settingsReader = settingsReader;
        this.dimensionMappings = dimensionMappings;
        this.scoredItems = scoredItems;
        this.gateNumbersQuery = gateNumbersQuery;
        this.store = store;
        this.jdbc = jdbc;
        this.snapshot = new TransactionTemplate(transactionManager);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void checkArguments(JobArguments arguments) {
        arguments.names().stream()
                .filter(name -> !name.equals(QUARTER))
                .findFirst()
                .ifPresent(name -> {
                    throw new IllegalArgumentException("Unknown argument '" + name + "'. " + NAME + " takes quarter");
                });
        quarter(arguments);
    }

    @Override
    public void run(JobArguments arguments, JobCounts counts) {
        Quarter quarter = quarter(arguments);
        snapshot.executeWithoutResult(status -> {
            // The database allows one running run per job, so the running run is this one.
            long runId = jdbc.sql(RUNNING_RUN_ID)
                    .param("jobName", NAME)
                    .query(Long.class)
                    .single();
            Map<Long, List<DimensionScoreResult>> results = score(quarter);
            counts.add("persons", results.size());
            counts.add("scores_written", store.insert(runId, quarter, clock.instant(), results));
        });
    }

    private Map<Long, List<DimensionScoreResult>> score(Quarter quarter) {
        ScoringSettings settings = settingsReader.in(quarter);
        List<DimensionMapping> mappings = dimensionMappings.inForce(quarter);
        Map<Long, List<ScoredItem>> itemsByPerson = scoredItems.of(quarter);
        Map<Long, Map<Gate, GateNumbers>> gateNumbers = gateNumbersQuery.of(quarter, settings, itemsByPerson.keySet());
        DimensionScorer scorer = new DimensionScorer(settings);
        Map<Long, List<DimensionScoreResult>> resultsByPerson = new LinkedHashMap<>();
        itemsByPerson.forEach((personId, items) -> {
            List<DimensionScoreResult> results = new ArrayList<>();
            for (DimensionMapping mapping : mappings) {
                for (Level level : Level.values()) {
                    results.add(scorer.score(mapping, level, items, gateNumbers.get(personId)));
                }
            }
            resultsByPerson.put(personId, results);
        });
        return resultsByPerson;
    }

    private static Quarter quarter(JobArguments arguments) {
        String value = arguments.require(QUARTER);
        try {
            return Quarter.parse(value);
        } catch (IllegalArgumentException wrongForm) {
            throw new IllegalArgumentException(
                    "The argument quarter '" + value + "' is not of the form YYYY-Qn", wrongForm);
        }
    }
}
