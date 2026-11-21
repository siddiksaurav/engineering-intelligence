package com.dsinnovators.ei.evidence;

import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

/**
 * The exact version of a raw record an evidence item was built from, and the role the record had.
 * Written once with its item.
 */
@Embeddable
public record EvidenceItemSource(
        Long rawRecordId,

        @Convert(converter = SourceRole.DatabaseConverter.class)
        SourceRole role) {}
