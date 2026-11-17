package com.dsinnovators.ei.source;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface DataSourceRepository extends Repository<DataSource, Long> {

    @Query("select d from DataSource d where d.sourceKey = :key")
    Optional<DataSource> findByKey(@Param("key") String key);
}
