package com.dsinnovators.ei.source.openproject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/**
 * Turns references (a number, a key or an old key) into work package numbers on the server (decision 58).
 * References go in collection calls of up to one page. A reference the answer does not name, such as an
 * old key that the server answers under its new key, is fetched alone. One value the server does not accept
 * fails a whole collection call with {@code 400}; the references of that call are then fetched one at a
 * time. A reference answered {@code 404} is not visible to the public API.
 */
public class WorkPackageReferenceResolver {

    private static final String COLLECTION_CALL = "the work package collection call";

    private final OpenProjectApi api;
    private final OpenProjectProperties properties;

    WorkPackageReferenceResolver(OpenProjectApi api, OpenProjectProperties properties) {
        this.api = api;
        this.properties = properties;
    }

    /**
     * Resolves every distinct reference to a work package number or to "not visible".
     *
     * @param references references in upper case; a repeated one is asked for once
     * @return one entry per distinct reference, in the order the references first appear
     * @throws OpenProjectApiException when a call fails after its retries or an answer lacks a work package number
     */
    public List<ResolvedReference> resolve(Collection<String> references) throws InterruptedException {
        List<String> distinct = List.copyOf(new LinkedHashSet<>(references));
        Map<String, ResolvedReference> resolved = new HashMap<>();
        for (int start = 0; start < distinct.size(); start += properties.pageSize()) {
            List<String> page = distinct.subList(start, Math.min(start + properties.pageSize(), distinct.size()));
            resolvePage(page).forEach(result -> resolved.put(result.reference(), result));
        }
        return distinct.stream().map(resolved::get).toList();
    }

    private List<ResolvedReference> resolvePage(List<String> page) throws InterruptedException {
        JsonNode answer;
        try {
            answer = api.get(collectionPath(page));
        } catch (OpenProjectBadRequestException invalidValue) {
            return fetchEachAlone(page);
        }
        Map<String, Long> numbersByName = numbersByNameOf(answer);
        List<ResolvedReference> results = new ArrayList<>();
        for (String reference : page) {
            Long number = numbersByName.get(reference);
            results.add(number == null ? fetchAlone(reference) : ResolvedReference.resolved(reference, number));
        }
        return results;
    }

    private List<ResolvedReference> fetchEachAlone(List<String> references) throws InterruptedException {
        List<ResolvedReference> results = new ArrayList<>();
        for (String reference : references) {
            results.add(fetchAlone(reference));
        }
        return results;
    }

    private ResolvedReference fetchAlone(String reference) throws InterruptedException {
        try {
            JsonNode workPackage = api.get("/work_packages/" + URLEncoder.encode(reference, StandardCharsets.UTF_8));
            return ResolvedReference.resolved(reference, numberOf(workPackage, "the work package " + reference));
        } catch (OpenProjectNotFoundException hidden) {
            return ResolvedReference.notVisible(reference);
        }
    }

    private String collectionPath(List<String> references) {
        String values =
                references.stream().map(reference -> "\"" + reference + "\"").collect(Collectors.joining(","));
        String filters = "[{\"id\":{\"operator\":\"=\",\"values\":[" + values + "]}}]";
        return "/work_packages?filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8) + "&pageSize="
                + properties.pageSize();
    }

    /** Both the number and the current key of each element name it; an old key names nothing. */
    private static Map<String, Long> numbersByNameOf(JsonNode collection) {
        JsonNode elements = collection.path("_embedded").path("elements");
        if (!elements.isArray()) {
            throw new OpenProjectApiException(
                    "OpenProject's answer to " + COLLECTION_CALL + " has no list of elements");
        }
        Map<String, Long> numbersByName = new HashMap<>();
        for (JsonNode element : elements) {
            long number = numberOf(element, "an element of " + COLLECTION_CALL);
            numbersByName.put(String.valueOf(number), number);
            numbersByName.put(element.path("displayId").asString(""), number);
        }
        return numbersByName;
    }

    private static long numberOf(JsonNode workPackage, String what) {
        JsonNode id = workPackage.path("id");
        if (!id.isIntegralNumber()) {
            throw new OpenProjectApiException("OpenProject's answer for " + what + " has no numeric id");
        }
        return id.asLong();
    }
}
