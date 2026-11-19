package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.RawRecord;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Finds stored activities by the work package they name; raw records are read, never written. */
interface WorkPackageActivityRecords extends Repository<RawRecord, Long> {

    @Query(nativeQuery = true, value = """
            select r.* from raw_record r
            join data_source d on d.id = r.data_source_id
            where d.key = :sourceKey and r.record_kind = :recordKind
              and r.payload #>> '{_links,workPackage,href}' = :workPackageHref
              and r.version = (select max(o.version) from raw_record o
                               where o.data_source_id = r.data_source_id
                                 and o.record_kind = r.record_kind
                                 and o.external_id = r.external_id)
            """)
    List<RawRecord> findLatestOfWorkPackage(
            @Param("sourceKey") String sourceKey,
            @Param("recordKind") String recordKind,
            @Param("workPackageHref") String workPackageHref);
}
