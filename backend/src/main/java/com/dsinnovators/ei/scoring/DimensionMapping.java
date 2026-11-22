package com.dsinnovators.ei.scoring;

import com.dsinnovators.ei.common.Quarter;
import java.util.List;
import java.util.Optional;

/**
 * A dimension with the evidence types and gates in force for a quarter. The versions are the
 * {@code valid_from_quarter} of the rows used, so a trail can name them (spec 8.6).
 *
 * @param gatesVersion empty when the dimension has no gate
 */
public record DimensionMapping(
        long dimensionId,
        String key,
        String name,
        Quarter evidenceTypesVersion,
        List<MappedEvidenceType> evidenceTypes,
        Optional<Quarter> gatesVersion,
        List<MappedGate> gates) {

    public DimensionMapping {
        evidenceTypes = List.copyOf(evidenceTypes);
        gates = List.copyOf(gates);
    }
}
