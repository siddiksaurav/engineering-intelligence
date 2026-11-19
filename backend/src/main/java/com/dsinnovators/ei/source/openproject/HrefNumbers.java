package com.dsinnovators.ei.source.openproject;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the number at the end of an OpenProject link such as {@code /api/v3/work_packages/81224}. */
final class HrefNumbers {

    private static final Pattern TRAILING_NUMBER = Pattern.compile("/([0-9]+)$");

    private HrefNumbers() {}

    /** The number at the end of the link; empty for a link without one, such as a null link. */
    static Optional<Long> trailingNumber(String href) {
        Matcher matcher = TRAILING_NUMBER.matcher(href);
        return matcher.find() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }
}
