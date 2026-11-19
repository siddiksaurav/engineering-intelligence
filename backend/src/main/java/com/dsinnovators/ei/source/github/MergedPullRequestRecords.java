package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries on the stored {@code pull_request} records; the jsonb merge date needs SQL. */
interface MergedPullRequestRecords extends Repository<RawRecord, Long> {

    @Query(nativeQuery = true, value = """
            select r.* from raw_record r
            join data_source d on d.id = r.data_source_id
            where d.key = :dataSourceKey and r.record_kind = :recordKind
              and starts_with(r.external_id, :externalIdPrefix)
              and r.version = (select max(o.version) from raw_record o
                               where o.data_source_id = r.data_source_id
                                 and o.record_kind = r.record_kind
                                 and o.external_id = r.external_id)
              and (r.payload ->> 'mergedAt')::timestamptz >= :mergedFrom
              and (r.payload ->> 'mergedAt')::timestamptz < :mergedBefore
            order by (r.payload ->> 'mergedAt')::timestamptz, r.id
            """)
    List<RawRecord> findLatestMergedBetween(
            @Param("dataSourceKey") String dataSourceKey,
            @Param("recordKind") String recordKind,
            @Param("externalIdPrefix") String externalIdPrefix,
            @Param("mergedFrom") Instant mergedFrom,
            @Param("mergedBefore") Instant mergedBefore);
}
