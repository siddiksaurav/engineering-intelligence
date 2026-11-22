package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.analytics.AnalyticsQuery;
import com.dsinnovators.ei.analytics.AnalyticsSql;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.evidence.EvidenceType;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persons to score in a quarter and their current evidence items (decision 227): a person is
 * scored with at least one current item dated in the quarter or a human pull request merged in it.
 * An item carries the tier of its current judgment, whatever the kind of the judgment is.
 */
@Component
public class ScoredItems {

    private static final String PERSONS_QUERY =
            AnalyticsSql.with(List.of(AnalyticsSql.HUMAN_PULL_REQUESTS, AnalyticsSql.CURRENT_ITEM), """
            select person_id from human_pull_request where person_id is not null
            union
            select person_id from current_item where occurred_on between :firstDay and :lastDay
            order by person_id
            """);

    // The kind of judgment that gives an item its tier; a type without a kind has no tier judgment.
    private static final String JUDGMENT_KIND = """
            case item.evidence_type
                when 'feature_work' then 'feature_work_complexity'
                when 'review_comment_written' then 'review_comment'
                when 'test_change' then 'item_tier'
                when 'refactor_performance' then 'item_tier'
                when 'security' then 'item_tier'
                when 'infra_release' then 'item_tier'
            end""";

    // An item rests on its pull requests as source records of the role pull_request, a review comment as well.
    private static final String ITEMS_QUERY = AnalyticsSql.with(
            List.of(AnalyticsSql.CURRENT_ITEMS_AND_JUDGMENTS), """
            select item.id,
                   item.person_id,
                   item.item_key,
                   item.evidence_type,
                   item.source_url,
                   judgment.id as judgment_id,
                   judgment.tier,
                   judgment.value ->> 'complexity' as complexity,
                   array(select distinct (record.payload ->> 'number')::int
                         from evidence_item_source source
                         join raw_record record on record.id = source.raw_record_id
                         where source.evidence_item_id = item.id and source.role = 'pull_request'
                         order by 1) as pull_request_numbers
            from current_item item
            left join current_judgment judgment
                   on judgment.evidence_item_id = item.id and judgment.kind = {judgmentKind}
            where item.occurred_on between :firstDay and :lastDay
            order by item.id
            """.replace("{judgmentKind}", JUDGMENT_KIND));

    private final AnalyticsQuery analyticsQuery;

    ScoredItems(AnalyticsQuery analyticsQuery) {
        this.analyticsQuery = analyticsQuery;
    }

    /** The current items of the quarter by the id of each person to score; a person without items has an empty list. */
    @Transactional(readOnly = true)
    public Map<Long, List<ScoredItem>> of(Quarter quarter) {
        Map<Long, List<ScoredItem>> itemsByPerson = new LinkedHashMap<>();
        analyticsQuery
                .statement(PERSONS_QUERY, quarter)
                .query((row, rowNumber) -> row.getLong("person_id"))
                .list()
                .forEach(personId -> itemsByPerson.put(personId, new ArrayList<>()));
        analyticsQuery
                .statement(ITEMS_QUERY, quarter)
                .query((row, rowNumber) -> new PersonItem(row.getLong("person_id"), itemOfRow(row)))
                .list()
                .forEach(personItem -> itemsByPerson
                        .computeIfAbsent(personItem.personId(), personId -> new ArrayList<>())
                        .add(personItem.item()));
        return itemsByPerson;
    }

    private record PersonItem(long personId, ScoredItem item) {}

    private static ScoredItem itemOfRow(ResultSet row) throws SQLException {
        EvidenceType evidenceType = EvidenceType.fromDatabaseName(row.getString("evidence_type"));
        String tier = row.getString("tier");
        return new ScoredItem(
                row.getLong("id"),
                row.getString("item_key"),
                evidenceType,
                judgmentStateOf(row.getObject("judgment_id") != null, tier != null, evidenceType),
                tier == null ? null : Tier.fromDatabase(tier),
                row.getString("complexity"),
                numbersOf(row.getArray("pull_request_numbers")),
                row.getString("source_url"));
    }

    // A review comment with a judgment and no tier is one that is not substantive (spec 7.2).
    private static JudgmentState judgmentStateOf(boolean hasJudgment, boolean hasTier, EvidenceType evidenceType) {
        if (hasTier) {
            return JudgmentState.JUDGED;
        }
        if (hasJudgment && evidenceType == EvidenceType.REVIEW_COMMENT_WRITTEN) {
            return JudgmentState.NOT_SUBSTANTIVE;
        }
        return JudgmentState.NOT_JUDGED_YET;
    }

    private static List<Integer> numbersOf(Array column) throws SQLException {
        List<Integer> numbers = new ArrayList<>();
        for (Object number : (Object[]) column.getArray()) {
            numbers.add(((Number) number).intValue());
        }
        return numbers;
    }
}
