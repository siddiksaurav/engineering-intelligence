package com.dsinnovators.ei.setting;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface SettingRepository extends Repository<Setting, Long> {

    /**
     * The row for a key with the latest quarter that is not after the given one. The text form
     * {@code YYYY-Qn} sorts in time order.
     */
    @Query("""
            select s from Setting s
            where s.settingKey = :key and s.validFromQuarter <= :quarter
            order by s.validFromQuarter desc
            limit 1
            """)
    Optional<Setting> findInForce(@Param("key") String key, @Param("quarter") String quarter);
}
