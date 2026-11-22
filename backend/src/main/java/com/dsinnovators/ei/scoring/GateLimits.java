package com.dsinnovators.ei.scoring;

/**
 * The points a person may be below the repository share and still pass a gate, and the points beyond
 * which it is a far miss (spec 7.3).
 */
public record GateLimits(int passWithinPoints, int farMissBeyondPoints) {

    public GateLimits {
        if (passWithinPoints < 0 || farMissBeyondPoints < passWithinPoints) {
            throw new IllegalArgumentException("Gate limits need 0 <= passWithinPoints <= farMissBeyondPoints, got "
                    + passWithinPoints + " and " + farMissBeyondPoints);
        }
    }
}
