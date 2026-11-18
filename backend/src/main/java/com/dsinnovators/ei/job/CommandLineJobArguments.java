package com.dsinnovators.ei.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads {@code --key=value} and {@code --key} arguments. {@code --job} is not passed on to the job. */
final class CommandLineJobArguments implements JobArguments {

    static final String JOB = "job";

    private static final String PREFIX = "--";

    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> jobNames = new ArrayList<>();

    /**
     * Reads the arguments as they came from the command line.
     *
     * @throws IllegalArgumentException for an argument that does not start with {@code --} and for
     *     an argument other than {@code --job} that is given more than once
     */
    CommandLineJobArguments(String... args) {
        for (String arg : args) {
            if (arg == null || !arg.startsWith(PREFIX)) {
                throw new IllegalArgumentException("Argument '" + arg + "' must have the form --key=value or --key");
            }
            int equals = arg.indexOf('=');
            String name = equals < 0 ? arg.substring(PREFIX.length()) : arg.substring(PREFIX.length(), equals);
            String value = equals < 0 ? "" : arg.substring(equals + 1);
            if (name.equals(JOB)) {
                jobNames.add(value);
            } else if (values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Argument --" + name + " is given more than once");
            }
        }
    }

    /** Every value given for {@code --job}, in order. */
    List<String> jobNames() {
        return List.copyOf(jobNames);
    }

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    @Override
    public String require(String name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("Missing argument --" + name));
    }
}
