package com.dsinnovators.ei.source.openproject;

import com.dsinnovators.ei.source.DataSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Writes and looks up the {@code project} table. Not a source copy: a changed name or identifier updates the row.
 * No feature reads a project row through an entity, so there is none.
 */
interface ProjectRepository extends Repository<DataSource, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into project (data_source_id, external_id, identifier, name, url)
            select d.id, :externalId, :identifier, :name, :url from data_source d where d.key = :sourceKey
            on conflict (data_source_id, external_id)
            do update set identifier = excluded.identifier, name = excluded.name, url = excluded.url
            """)
    void upsert(
            @Param("sourceKey") String sourceKey,
            @Param("externalId") String externalId,
            @Param("identifier") String identifier,
            @Param("name") String name,
            @Param("url") String url);

    @Query(nativeQuery = true, value = """
            select p.id from project p join data_source d on d.id = p.data_source_id
            where d.key = :sourceKey and p.external_id = :externalId
            """)
    Optional<Long> findId(@Param("sourceKey") String sourceKey, @Param("externalId") String externalId);
}
