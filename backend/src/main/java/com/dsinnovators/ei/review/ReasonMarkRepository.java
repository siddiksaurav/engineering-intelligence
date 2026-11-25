package com.dsinnovators.ei.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** No update and no delete: only {@code save} of a new row, and finders. The current mark of an item is the row no other row supersedes. */
interface ReasonMarkRepository extends Repository<ReasonMark, Long> {

    ReasonMark save(ReasonMark mark);

    @Query("""
            select m from ReasonMark m
            where m.blindCheckItemId = :blindCheckItemId
              and not exists (select 1 from ReasonMark s where s.supersedesId = m.id)
            """)
    Optional<ReasonMark> findCurrentOfItem(@Param("blindCheckItemId") long blindCheckItemId);

    @Query("""
            select m from ReasonMark m
            where m.blindCheckItemId in :blindCheckItemIds
              and not exists (select 1 from ReasonMark s where s.supersedesId = m.id)
            """)
    List<ReasonMark> findCurrentOfItems(@Param("blindCheckItemIds") Collection<Long> blindCheckItemIds);
}
