package com.dsinnovators.ei.judgment;

/**
 * How much of a pull request goes into one digest call.
 *
 * @param maxBodyCharacters where the description is cut
 * @param maxDiffCharacters the budget of patch text (decision 155)
 */
record DigestLimits(int maxBodyCharacters, int maxDiffCharacters) {

    DigestLimits {
        if (maxBodyCharacters <= 0 || maxDiffCharacters <= 0) {
            throw new IllegalArgumentException(
                    "Digest limits must be more than zero, were " + maxBodyCharacters + " and " + maxDiffCharacters);
        }
    }
}
