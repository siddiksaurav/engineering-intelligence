package com.dsinnovators.ei.api;

import com.dsinnovators.ei.review.ItemBlindCheck;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * One evidence item row: its summary, its current judgment and, for an item of the blind check sample, the
 * user's label and reason mark ({@code blindCheck} is null for any other item).
 */
record EvidenceItemDetailResponse(
        @JsonUnwrapped EvidenceItemSummaryResponse summary, ItemJudgmentResponse judgment, ItemBlindCheck blindCheck) {}
