package com.dsinnovators.ei.job;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class MapJobArguments implements JobArguments {

    private final Map<String, String> values;

    MapJobArguments(Map<String, String> values) {
        Map<String, String> copy = new TreeMap<>();
        values.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("An argument has no name");
            }
            if (value == null) {
                throw new IllegalArgumentException("The argument '" + name + "' has no value");
            }
            copy.put(name, value);
        });
        this.values = Collections.unmodifiableMap(copy);
    }

    @Override
    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    @Override
    public String require(String name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("The argument '" + name + "' is required"));
    }

    @Override
    public Set<String> names() {
        return values.keySet();
    }

    @Override
    public Map<String, String> asMap() {
        return values;
    }
}
