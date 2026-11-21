package com.dsinnovators.ei.judgment;

import java.util.Locale;

/** An enum whose JSON value is its lower-case name. */
interface WireNamed {

    String name();

    default String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
