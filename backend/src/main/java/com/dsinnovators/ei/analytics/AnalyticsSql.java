package com.dsinnovators.ei.analytics;

import java.util.List;

/**
 * The query pieces several analytics share, each written once as a named common table expression:
 * the latest version of a stored pull request, the human authors of pull requests merged in the
 * quarter, the current item, the current judgment and the current digest of a pull request.
 * Queries take the named parameters {@code :firstDay}, {@code :lastDay} and {@code :botLogins}
 * from {@link AnalyticsQuery}.
 */
final class AnalyticsSql {

    private static final String LATEST_PULL_REQUEST = """
            latest_pull_request as (
                select distinct on (record.external_id) record.id, record.external_id, record.payload
                from raw_record record
                join data_source source on source.id = record.data_source_id
                where source.key = 'github' and record.record_kind = 'pull_request'
                order by record.external_id, record.version desc
            )""";

    // The merge day is the UTC date of mergedAt, as everywhere else (decisions 121, 150).
    private static final String HUMAN_PULL_REQUEST = """
            human_pull_request as (
                select latest.id as record_id,
                       latest.external_id,
                       (latest.payload ->> 'number')::int as number,
                       identity.person_id
                from latest_pull_request latest
                left join person_identity identity
                       on identity.data_source_id = (select id from data_source where key = 'github')
                      and identity.external_id = latest.payload #>> '{author,databaseId}'
                where latest.payload #>> '{author,__typename}' = 'User'
                  and coalesce(latest.payload #>> '{author,login}', '')
                      not in (select jsonb_array_elements_text(cast(:botLogins as jsonb)))
                  and ((latest.payload ->> 'mergedAt')::timestamptz at time zone 'UTC')::date
                      between :firstDay and :lastDay
            )""";

    private static final String CURRENT_ITEM = """
            current_item as (
                select item.*
                from evidence_item item
                where not item.withdrawn
                  and not exists (select 1 from evidence_item newer where newer.supersedes_id = item.id)
            )""";

    private static final String CURRENT_JUDGMENT = """
            current_judgment as (
                select judgment.*
                from judgment
                where not exists (select 1 from judgment newer where newer.supersedes_id = judgment.id)
            )""";

    // A digest sits on one version of the pull request record, any version counts (PullRequestDigests.currentOf).
    private static final String CURRENT_DIGEST = """
            current_digest as (
                select distinct on (record.external_id) record.external_id, digest.value
                from current_judgment digest
                join raw_record record on record.id = digest.raw_record_id
                where digest.kind = 'pull_request_digest'
                order by record.external_id, digest.id desc
            )""";

    static final String HUMAN_PULL_REQUESTS = LATEST_PULL_REQUEST + ",\n" + HUMAN_PULL_REQUEST;

    static final String CURRENT_DIGESTS = CURRENT_JUDGMENT + ",\n" + CURRENT_DIGEST;

    static final String CURRENT_ITEMS_AND_JUDGMENTS = CURRENT_ITEM + ",\n" + CURRENT_JUDGMENT;

    private AnalyticsSql() {}

    /** A query that starts with the given common table expressions and continues with {@code select}. */
    static String with(List<String> expressions, String select) {
        return "with " + String.join(",\n", expressions) + "\n" + select;
    }
}
