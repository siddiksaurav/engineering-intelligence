package com.dsinnovators.ei.scoring;

/**
 * The extra rule at level L3 (decision 220): T3 counts only with enough T3 items across the dimension.
 *
 * @param t3Items the T3 items counted across the tiered types read at the level
 * @param t3ItemsNeeded the setting value for the dimension
 * @param met whether {@code t3Items} reaches {@code t3ItemsNeeded}
 */
public record LevelThreeRule(long t3Items, int t3ItemsNeeded, boolean met) {}
