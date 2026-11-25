package com.dsinnovators.ei.review;

/**
 * A count out of the items that can count, and its share.
 *
 * @param share {@code count / of}; null when {@code of} is 0, because a share of nothing is not 0
 */
public record Rate(int count, int of, Double share) {

    static Rate of(int count, int of) {
        return new Rate(count, of, of == 0 ? null : (double) count / of);
    }
}
