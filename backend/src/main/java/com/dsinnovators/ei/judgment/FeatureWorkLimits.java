package com.dsinnovators.ei.judgment;

/**
 * How much of a Feature work item goes into one Stage 2 call.
 *
 * @param maxDescriptionCharacters where a work item description or a pull request body is cut
 * @param maxFeatureWorkCharacters the budget of the whole input (decision 168)
 */
record FeatureWorkLimits(int maxDescriptionCharacters, int maxFeatureWorkCharacters) {

    FeatureWorkLimits {
        if (maxDescriptionCharacters <= 0 || maxFeatureWorkCharacters <= 0) {
            throw new IllegalArgumentException("Feature work limits must be more than zero, were "
                    + maxDescriptionCharacters + " and " + maxFeatureWorkCharacters);
        }
    }
}
