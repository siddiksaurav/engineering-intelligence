package com.dsinnovators.ei.evidence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * No update and no delete: only {@code save} of a new row, and finders. The current row of a key is
 * the row that no other row supersedes; a withdrawn row ends the item, so it is no current item.
 */
interface EvidenceItemRepository extends Repository<EvidenceItem, Long> {

    EvidenceItem save(EvidenceItem item);

    /** The row no other row supersedes, withdrawn or not: the row the next row of the key replaces. */
    @Query("""
            select e from EvidenceItem e
            where e.itemKey = :itemKey
              and not exists (select 1 from EvidenceItem s where s.supersedesId = e.id)
            """)
    Optional<EvidenceItem> findLatestRowOfKey(@Param("itemKey") String itemKey);

    @Query("""
            select e from EvidenceItem e
            where e.itemKey = :itemKey and e.withdrawn = false
              and not exists (select 1 from EvidenceItem s where s.supersedesId = e.id)
            """)
    Optional<EvidenceItem> findCurrentByKey(@Param("itemKey") String itemKey);

    @Query("""
            select e from EvidenceItem e
            where e.withdrawn = false
              and not exists (select 1 from EvidenceItem s where s.supersedesId = e.id)
            order by e.id
            """)
    List<EvidenceItem> findAllCurrent();

    @Query("""
            select e from EvidenceItem e
            where e.personId = :personId and e.withdrawn = false
              and e.occurredOn between :firstDay and :lastDay
              and not exists (select 1 from EvidenceItem s where s.supersedesId = e.id)
            order by e.occurredOn desc, e.id desc
            """)
    List<EvidenceItem> findCurrentOfPersonBetween(
            @Param("personId") long personId,
            @Param("firstDay") LocalDate firstDay,
            @Param("lastDay") LocalDate lastDay);

    /** The rows with these ids, whatever their state. */
    List<EvidenceItem> findAllById(Iterable<Long> ids);

    @Query("""
            select e from EvidenceItem e
            where e.itemKey in :itemKeys and e.withdrawn = false
              and not exists (select 1 from EvidenceItem s where s.supersedesId = e.id)
            """)
    List<EvidenceItem> findCurrentByKeys(@Param("itemKeys") Collection<String> itemKeys);
}
