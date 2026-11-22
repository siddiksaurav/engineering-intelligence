package com.dsinnovators.ei.scoring;

import java.util.List;

/**
 * The working tier of a dimension for a person at a level, with the counting behind it.
 *
 * @param tier the highest tier with presence in any type; null when there is none
 * @param levelThreeRule null when the rule does not apply
 */
public record WorkingTier(Tier tier, List<EvidenceTypeTrail> evidenceTypes, LevelThreeRule levelThreeRule) {}
