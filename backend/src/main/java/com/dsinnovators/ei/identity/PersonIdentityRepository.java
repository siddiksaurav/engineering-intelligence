package com.dsinnovators.ei.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface PersonIdentityRepository extends Repository<PersonIdentity, Long> {

    PersonIdentity save(PersonIdentity identity);

    @Query("""
            select i from PersonIdentity i
            where i.dataSource.sourceKey = :dataSourceKey and i.externalId = :externalId
            """)
    Optional<PersonIdentity> find(@Param("dataSourceKey") String dataSourceKey, @Param("externalId") String externalId);
}
