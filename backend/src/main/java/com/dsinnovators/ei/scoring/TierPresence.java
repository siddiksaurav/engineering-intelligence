package com.dsinnovators.ei.scoring;

/**
 * Presence of one evidence type at one tier.
 *
 * @param atOrAbove the weight (Feature work) or count of counted items at the tier or higher
 * @param total the weight or count of all counted items of the type
 */
public record TierPresence(Tier tier, long atOrAbove, long total, boolean presence) {}
