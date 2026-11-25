package com.dsinnovators.ei.review;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** No update and no delete: only {@code save} of a new row, and finders. */
interface HumanLabelRepository extends Repository<HumanLabel, Long> {

    HumanLabel save(HumanLabel label);

    List<HumanLabel> findByBlindCheckItemIdIn(Collection<Long> blindCheckItemIds);
}
