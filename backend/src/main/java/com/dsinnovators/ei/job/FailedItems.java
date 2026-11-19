package com.dsinnovators.ei.job;

import java.util.Collection;
import java.util.stream.Collectors;

/** Names the items that failed in the message of a run that fails at its end. */
public final class FailedItems {

    private static final int NAMES_SHOWN = 20;

    private FailedItems() {}

    /** The first twenty names, separated by commas; a long list stays readable. */
    public static String named(Collection<String> names) {
        return names.stream().limit(NAMES_SHOWN).collect(Collectors.joining(", "));
    }
}
