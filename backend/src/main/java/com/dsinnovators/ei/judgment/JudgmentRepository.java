package com.dsinnovators.ei.judgment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * No update and no delete: only {@code save} of a new row, and finders. The current judgment of a
 * subject and kind is the row no other row supersedes.
 */
interface JudgmentRepository extends Repository<Judgment, Long> {

    Judgment save(Judgment judgment);

    Optional<Judgment> findById(Long id);

    boolean existsByKindAndInputHashAndJudgedBy(JudgmentKind kind, String inputHash, JudgedBy judgedBy);

    @Query("""
            select j from Judgment j
            where j.kind = :kind and j.rawRecordId = :rawRecordId
              and not exists (select 1 from Judgment s where s.supersedesId = j.id)
            """)
    Optional<Judgment> findCurrentOfPullRequestRecord(
            @Param("kind") JudgmentKind kind, @Param("rawRecordId") long rawRecordId);

    @Query("""
            select j from Judgment j
            where j.kind = :kind and j.evidenceItemId = :evidenceItemId
              and not exists (select 1 from Judgment s where s.supersedesId = j.id)
            """)
    Optional<Judgment> findCurrentOfEvidenceItem(
            @Param("kind") JudgmentKind kind, @Param("evidenceItemId") long evidenceItemId);

    @Query("""
            select j from Judgment j
            where j.kind = :kind and j.evidenceItemId in :evidenceItemIds
              and not exists (select 1 from Judgment s where s.supersedesId = j.id)
            """)
    List<Judgment> findCurrentOfEvidenceItems(
            @Param("kind") JudgmentKind kind, @Param("evidenceItemIds") Collection<Long> evidenceItemIds);

    @Query("""
            select j from Judgment j
            where j.kind = :kind
              and j.rawRecordId in (select r.id from RawRecord r
                                    where r.recordKind = :recordKind and r.externalId = :externalId)
              and not exists (select 1 from Judgment s where s.supersedesId = j.id)
            """)
    Optional<Judgment> findCurrentOfPullRequest(
            @Param("kind") JudgmentKind kind,
            @Param("recordKind") String recordKind,
            @Param("externalId") String externalId);
}
