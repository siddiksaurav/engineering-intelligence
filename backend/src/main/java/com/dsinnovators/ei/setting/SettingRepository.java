package com.dsinnovators.ei.setting;

import java.util.Optional;
import org.springframework.data.repository.Repository;

interface SettingRepository extends Repository<Setting, Long> {

    /**
     * The row for a key with the latest quarter that is not after the given one. The text form
     * {@code YYYY-Qn} sorts in time order.
     */
    Optional<Setting> findFirstBySettingKeyAndValidFromQuarterLessThanEqualOrderByValidFromQuarterDesc(
            String settingKey, String quarter);
}
