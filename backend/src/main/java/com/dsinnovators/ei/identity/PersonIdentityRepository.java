package com.dsinnovators.ei.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Deletes at once, so the identity of the next proposal can be saved in the same transaction. */
    @Modifying
    @Query("""
            delete from PersonIdentity i
            where i.person.id = :personId
              and i.dataSource.id = (select d.id from DataSource d where d.sourceKey = :dataSourceKey)
            """)
    void deleteOfPerson(@Param("dataSourceKey") String dataSourceKey, @Param("personId") long personId);

    @Modifying
    @Query("""
            delete from PersonIdentity i
            where i.externalId = :externalId
              and i.dataSource.id = (select d.id from DataSource d where d.sourceKey = :dataSourceKey)
            """)
    void deleteOfExternalId(@Param("dataSourceKey") String dataSourceKey, @Param("externalId") String externalId);
}
