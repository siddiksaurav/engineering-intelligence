package com.dsinnovators.ei.source.openproject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Builds the path of a filtered call on the work package collection; the one place that spells a filter. */
final class WorkPackageCollectionPath {

    private WorkPackageCollectionPath() {}

    /** The work packages with one of the given ids, numbers or keys. */
    static String withIds(Collection<String> references, int pageSize) {
        return filtered("id", references, pageSize);
    }

    /** The whole tree under a work package, at any depth. */
    static String underAncestor(long number, int pageSize) {
        return filtered("ancestor", List.of(String.valueOf(number)), pageSize);
    }

    private static String filtered(String filterName, Collection<String> values, int pageSize) {
        String quoted = values.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(","));
        String filters = "[{\"" + filterName + "\":{\"operator\":\"=\",\"values\":[" + quoted + "]}}]";
        return "/work_packages?filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8) + "&pageSize=" + pageSize;
    }
}
