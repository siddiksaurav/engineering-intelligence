package com.dsinnovators.ei.source.github;

import com.dsinnovators.ei.source.RawRecord;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries on the author of stored GitHub records; the jsonb author needs SQL. */
interface StoredAccountRecords extends Repository<RawRecord, Long> {

    /** One account as a row of {@link #findAccounts}. */
    interface AccountRow {

        long getDatabaseId();

        String getLogin();

        String getTypename();
    }

    /**
     * Each account once, over the latest version of every record of the given kinds, with the login
     * and type of the most recently fetched record that names it.
     */
    @Query(nativeQuery = true, value = """
            select distinct on (account.database_id)
                   account.database_id as databaseId, account.login as login, account.typename as typename
            from (
                select (r.payload -> 'author' ->> 'databaseId')::bigint as database_id,
                       r.payload -> 'author' ->> 'login' as login,
                       r.payload -> 'author' ->> '__typename' as typename,
                       r.fetched_at, r.id
                from raw_record r
                join data_source d on d.id = r.data_source_id
                where d.key = :dataSourceKey and r.record_kind in (:recordKinds)
                  and r.payload -> 'author' ->> 'databaseId' is not null
                  and r.version = (select max(o.version) from raw_record o
                                   where o.data_source_id = r.data_source_id
                                     and o.record_kind = r.record_kind
                                     and o.external_id = r.external_id)
            ) account
            order by account.database_id, account.fetched_at desc, account.id desc
            """)
    List<AccountRow> findAccounts(
            @Param("dataSourceKey") String dataSourceKey, @Param("recordKinds") List<String> recordKinds);

    @Query(nativeQuery = true, value = """
            select r.external_id
            from raw_record r
            join data_source d on d.id = r.data_source_id
            where d.key = :dataSourceKey and r.record_kind = :recordKind
              and (r.payload -> 'author' ->> 'databaseId')::bigint = :databaseId
              and r.version = (select max(o.version) from raw_record o
                               where o.data_source_id = r.data_source_id
                                 and o.record_kind = r.record_kind
                                 and o.external_id = r.external_id)
            order by r.id
            """)
    List<String> findExternalIdsAuthoredBy(
            @Param("dataSourceKey") String dataSourceKey,
            @Param("recordKind") String recordKind,
            @Param("databaseId") long databaseId);
}
