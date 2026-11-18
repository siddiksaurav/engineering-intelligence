package com.dsinnovators.ei.source;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** No update and no delete: only {@code save} of a new row, and finders. */
interface RawRecordRepository extends Repository<RawRecord, Long> {

    RawRecord save(RawRecord record);

    @Query("""
            select r from RawRecord r
            where r.dataSource = :dataSource and r.recordKind = :recordKind and r.externalId = :externalId
            order by r.version desc
            limit 1
            """)
    Optional<RawRecord> findLatestVersion(
            @Param("dataSource") DataSource dataSource,
            @Param("recordKind") String recordKind,
            @Param("externalId") String externalId);

    @Query("""
            select r from RawRecord r
            where r.dataSource = :dataSource and r.recordKind = :recordKind and r.externalId = :externalId
            order by r.version
            """)
    List<RawRecord> findVersions(
            @Param("dataSource") DataSource dataSource,
            @Param("recordKind") String recordKind,
            @Param("externalId") String externalId);

    @Query("""
            select r from RawRecord r
            where r.dataSource = :dataSource and r.recordKind = :recordKind
              and r.version = (select max(o.version) from RawRecord o
                               where o.dataSource = r.dataSource
                                 and o.recordKind = r.recordKind
                                 and o.externalId = r.externalId)
            order by r.id
            """)
    List<RawRecord> findLatestByKind(
            @Param("dataSource") DataSource dataSource, @Param("recordKind") String recordKind);
}
