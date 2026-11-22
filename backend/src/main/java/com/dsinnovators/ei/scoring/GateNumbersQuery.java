package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.analytics.AnalyticsQuery;
import com.dsinnovators.ei.analytics.AnalyticsSql;
import com.dsinnovators.ei.common.Quarter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers gates G1 and G2 are checked with, for every person of a quarter in one call (spec 7.3).
 * G1 looks at the person's human pull requests merged in the quarter whose current digest says
 * feature or enhancement work (decision 208). G2 looks at the pull requests of the person's current
 * Bug fix items of the quarter, of both kinds. A pull request is tested when a current Test change
 * item rests on it. The repository numbers pool all persons, the person included (decision 223);
 * a developer counts in the group with enough pull requests of the kind (decision 224).
 */
@Component
public class GateNumbersQuery {

    /** The digest work types that make a pull request feature work for G1 (decision 208). */
    private static final List<String> GATE_ONE_WORK_TYPES = List.of("feature", "enhancement");

    private static final String GATE_ONE_QUERY = AnalyticsSql.with(
            List.of(
                    AnalyticsSql.HUMAN_PULL_REQUESTS,
                    AnalyticsSql.CURRENT_ITEMS_JUDGMENTS_AND_DIGESTS,
                    AnalyticsSql.TEST_CHANGE_ITEM),
            """
            select pull_request.person_id,
                   pull_request.number,
                   digest.value is null as without_digest,
                   exists (select 1 from test_change_item test_change
                           where test_change.pull_request_external_id = pull_request.external_id) as tested
            from human_pull_request pull_request
            left join current_digest digest on digest.external_id = pull_request.external_id
            where pull_request.person_id is not null
              and (digest.value is null or digest.value ->> 'workType' in (:workTypes))
            """);

    private static final String GATE_TWO_QUERY =
            AnalyticsSql.with(List.of(AnalyticsSql.CURRENT_ITEM, AnalyticsSql.TEST_CHANGE_ITEM), """
            select distinct item.person_id,
                   (record.payload ->> 'number')::int as number,
                   false as without_digest,
                   exists (select 1 from test_change_item test_change
                           where test_change.pull_request_external_id = record.external_id) as tested
            from current_item item
            join evidence_item_source source
                   on source.evidence_item_id = item.id and source.role = 'pull_request'
            join raw_record record on record.id = source.raw_record_id
            where item.evidence_type = 'bug_fix'
              and item.person_id is not null
              and item.occurred_on between :firstDay and :lastDay
            """);

    private final AnalyticsQuery analyticsQuery;

    GateNumbersQuery(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /**
     * The numbers of gates G1 and G2 of each given person; a person without pull requests of a kind
     * has empty lists. The repository numbers pool every person with pull requests of the kind.
     */
    @Transactional(readOnly = true)
    public Map<Long, Map<Gate, GateNumbers>> of(Quarter quarter, ScoringSettings settings, Collection<Long> personIds) {
        Map<Gate, List<PullRequestRow>> rowsByGate = new EnumMap<>(Gate.class);
        rowsByGate.put(
                Gate.G1,
                analyticsQuery
                        .statement(GATE_ONE_QUERY, quarter)
                        .param("workTypes", GATE_ONE_WORK_TYPES)
                        .query(GateNumbersQuery::rowOf)
                        .list());
        rowsByGate.put(
                Gate.G2,
                analyticsQuery
                        .statement(GATE_TWO_QUERY, quarter)
                        .query(GateNumbersQuery::rowOf)
                        .list());
        Map<Long, Map<Gate, GateNumbers>> numbersByPerson = new LinkedHashMap<>();
        for (long personId : personIds) {
            Map<Gate, GateNumbers> numbersByGate = new EnumMap<>(Gate.class);
            rowsByGate.forEach((gate, rows) ->
                    numbersByGate.put(gate, numbersOf(personId, rows, settings.gateMinimumPullRequestsPerDeveloper())));
            numbersByPerson.put(personId, numbersByGate);
        }
        return numbersByPerson;
    }

    private record PullRequestRow(long personId, int number, boolean withoutDigest, boolean tested) {}

    private static PullRequestRow rowOf(ResultSet row, int rowNumber) throws SQLException {
        return new PullRequestRow(
                row.getLong("person_id"),
                row.getInt("number"),
                row.getBoolean("without_digest"),
                row.getBoolean("tested"));
    }

    private static GateNumbers numbersOf(long personId, List<PullRequestRow> rows, int minimumPullRequests) {
        List<PullRequestRow> ofPerson = rows.stream()
                .filter(row -> row.personId() == personId && !row.withoutDigest())
                .toList();
        Set<Integer> repositoryPullRequests = new TreeSet<>();
        Set<Integer> repositoryTested = new TreeSet<>();
        Map<Long, Integer> pullRequestsByDeveloper = new TreeMap<>();
        for (PullRequestRow row : rows) {
            if (row.withoutDigest()) {
                continue;
            }
            repositoryPullRequests.add(row.number());
            if (row.tested()) {
                repositoryTested.add(row.number());
            }
            pullRequestsByDeveloper.merge(row.personId(), 1, Integer::sum);
        }
        return new GateNumbers(
                ofPerson.stream()
                        .filter(PullRequestRow::tested)
                        .map(PullRequestRow::number)
                        .sorted()
                        .toList(),
                ofPerson.stream().map(PullRequestRow::number).sorted().toList(),
                List.copyOf(repositoryTested),
                List.copyOf(repositoryPullRequests),
                pullRequestsByDeveloper.entrySet().stream()
                        .filter(entry -> entry.getValue() >= minimumPullRequests)
                        .map(Map.Entry::getKey)
                        .toList(),
                rows.stream()
                        .filter(row -> row.personId() == personId && row.withoutDigest())
                        .map(PullRequestRow::number)
                        .distinct()
                        .sorted()
                        .toList());
    }
}
