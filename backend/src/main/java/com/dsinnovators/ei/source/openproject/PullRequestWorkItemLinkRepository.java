package com.dsinnovators.ei.source.openproject;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface PullRequestWorkItemLinkRepository extends Repository<PullRequestWorkItemLink, Long> {

    PullRequestWorkItemLink save(PullRequestWorkItemLink link);

    @Query("select l from PullRequestWorkItemLink l where l.pullRequestExternalId = :externalId order by l.id")
    List<PullRequestWorkItemLink> findOfPullRequest(@Param("externalId") String externalId);

    @Modifying
    @Query("delete from PullRequestWorkItemLink l where l.pullRequestExternalId = :externalId")
    void deleteOfPullRequest(@Param("externalId") String externalId);
}
