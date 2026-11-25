package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.BlindCheckItem;
import java.time.Instant;

/**
 * One item of the blind check sample. It names no judgment and no AI value: the sample answers are
 * what the user must not see before labeling.
 */
record SampledItemResponse(int position, long itemId, Instant pickedAt) {

    static SampledItemResponse of(BlindCheckItem item) {
        return new SampledItemResponse(item.getPosition(), item.getEvidenceItemId(), item.getPickedAt());
    }
}
