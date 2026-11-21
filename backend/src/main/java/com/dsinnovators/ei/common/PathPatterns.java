package com.dsinnovators.ei.common;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Path patterns as the settings write them. A pattern ending in {@code /} is a directory from the
 * repository root; one with no {@code /} is a file name at any depth; any other is the whole path.
 * {@code *} stands for any characters inside one folder or file name.
 */
public final class PathPatterns {

    private static final String SEPARATOR = "/";

    private record Rule(Pattern regex, boolean onFileName) {}

    private final List<Rule> rules;

    public PathPatterns(List<String> patterns) {
        this.rules = patterns.stream().map(PathPatterns::ruleOf).toList();
    }

    public boolean matches(String path) {
        String fileName = path.substring(path.lastIndexOf(SEPARATOR) + 1);
        return rules.stream()
                .anyMatch(rule -> rule.regex()
                        .matcher(rule.onFileName() ? fileName : path)
                        .matches());
    }

    private static Rule ruleOf(String pattern) {
        boolean directory = pattern.endsWith(SEPARATOR);
        String glob = Pattern.quote(pattern).replace("*", "\\E[^/]*\\Q");
        return new Rule(Pattern.compile(directory ? glob + ".*" : glob), !pattern.contains(SEPARATOR));
    }
}
