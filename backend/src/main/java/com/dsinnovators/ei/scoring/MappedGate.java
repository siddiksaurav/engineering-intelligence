package com.dsinnovators.ei.scoring;

/**
 * A gate on a dimension.
 *
 * @param applied false when the gate is not calculated and the trail shows "check unverified"
 */
public record MappedGate(Gate gate, boolean applied) {}
