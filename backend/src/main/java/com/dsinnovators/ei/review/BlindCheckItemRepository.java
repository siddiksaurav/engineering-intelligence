package com.dsinnovators.ei.review;

import java.util.List;
import org.springframework.data.repository.Repository;

/** No update and no delete: only {@code save} of a new row, and finders. */
interface BlindCheckItemRepository extends Repository<BlindCheckItem, Long> {

    BlindCheckItem save(BlindCheckItem item);

    boolean existsBy();

    List<BlindCheckItem> findAllByOrderByPositionAsc();
}
