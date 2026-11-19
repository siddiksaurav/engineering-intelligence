package com.dsinnovators.ei.source.openproject;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ProjectRepository extends Repository<Project, Long> {

    @Query("select p from Project p where p.dataSource.sourceKey = :sourceKey and p.externalId = :externalId")
    Optional<Project> findByExternalId(@Param("sourceKey") String sourceKey, @Param("externalId") String externalId);

    Project save(Project project);
}
