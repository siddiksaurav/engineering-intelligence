package com.dsinnovators.ei.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** One evidence item row: its summary and its current judgment. */
record EvidenceItemDetailResponse(@JsonUnwrapped EvidenceItemSummaryResponse summary, ItemJudgmentResponse judgment) {}
