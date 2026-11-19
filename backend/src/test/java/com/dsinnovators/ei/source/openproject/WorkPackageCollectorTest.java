package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.dsinnovators.ei.common.CanonicalJson;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class WorkPackageCollectorTest {

    private static final String UNDISCLOSED = "urn:openproject-org:api:v3:undisclosed";

    private final WorkPackageFetcher fetcher = mock(WorkPackageFetcher.class);
    private final WorkPackageCollector collector = new WorkPackageCollector(fetcher);
    private final ImportRun run = new ImportRun();

    private static String href(String target) {
        return "{\"href\":\"" + target + "\"}";
    }

    private static String workPackageLink(long number) {
        return href("/api/v3/work_packages/" + number);
    }

    private static JsonNode workPackage(long id, String type, List<String> ancestors, List<String> children) {
        return CanonicalJson.parse("{\"_type\":\"WorkPackage\",\"id\":" + id + ",\"_links\":{\"type\":{\"title\":\""
                + type + "\"},\"ancestors\":[" + String.join(",", ancestors) + "],\"children\":["
                + String.join(",", children) + "]}}");
    }

    private static JsonNode workPackage(long id, String type) {
        return workPackage(id, type, List.of(), List.of());
    }

    private static List<Long> numbersOf(List<JsonNode> workPackages) {
        return workPackages.stream().map(node -> node.path("id").asLong()).toList();
    }

    private void fetchReturns(Set<Long> numbers, JsonNode... workPackages) throws InterruptedException {
        when(fetcher.fetchWorkPackages(List.copyOf(numbers))).thenReturn(List.of(workPackages));
    }

    @Test
    void collectsTheReferencedWorkPackagesTheirAncestorsAndTheirDirectChildren() throws Exception {
        fetchReturns(Set.of(20L), workPackage(20, "Task", List.of(workPackageLink(10)), List.of(workPackageLink(30))));
        when(fetcher.fetchWorkPackages(List.of(10L, 30L)))
                .thenReturn(List.of(workPackage(10, "Epic"), workPackage(30, "Task")));

        List<JsonNode> added = collector.collect(List.of(20L), run);

        assertEquals(List.of(20L, 10L, 30L), numbersOf(added));
        assertEquals(List.of(20L, 10L, 30L), List.copyOf(run.collected().keySet()));
    }

    @Test
    void collectsTheWholeTreeUnderEveryFeatureAmongThem() throws Exception {
        fetchReturns(Set.of(20L), workPackage(20, "Feature"));
        when(fetcher.fetchTreeUnder(20)).thenReturn(List.of(workPackage(21, "Task"), workPackage(22, "Task")));

        List<JsonNode> added = collector.collect(List.of(20L), run);

        assertEquals(List.of(20L, 21L, 22L), numbersOf(added));
    }

    @Test
    void collectsNoTreeUnderAWorkPackageThatIsNotAFeature() throws Exception {
        fetchReturns(Set.of(20L), workPackage(20, "Epic"));

        collector.collect(List.of(20L), run);

        verify(fetcher, never()).fetchTreeUnder(20);
    }

    @Test
    void collectsTheTreeUnderAFeatureOnceInARun() throws Exception {
        fetchReturns(Set.of(20L), workPackage(20, "Feature"));
        when(fetcher.fetchTreeUnder(20)).thenReturn(List.of(workPackage(21, "Task")));
        collector.collect(List.of(20L), run);

        List<JsonNode> added = collector.collect(List.of(20L, 21L), run);

        assertEquals(List.of(), added);
        verify(fetcher).fetchTreeUnder(20);
    }

    @Test
    void doesNotFetchAgainAWorkPackageThatTheTreeOfAFeatureBroughtIn() throws Exception {
        fetchReturns(Set.of(20L), workPackage(20, "Feature"));
        when(fetcher.fetchTreeUnder(20)).thenReturn(List.of(workPackage(21, "Task")));
        collector.collect(List.of(20L), run);

        collector.collect(List.of(21L), run);

        verify(fetcher, never()).fetchWorkPackages(List.of(21L));
    }

    @Test
    void asksOnceForANumberThatTheApiDoesNotShow() throws Exception {
        when(fetcher.fetchWorkPackages(List.of(99L))).thenReturn(List.of());
        collector.collect(List.of(99L), run);

        List<JsonNode> added = collector.collect(List.of(99L), run);

        assertEquals(List.of(), added);
        verify(fetcher).fetchWorkPackages(List.of(99L));
    }

    @Test
    void aParentThatTheApiHidesEndsTheWalkUpWithoutAnError() throws Exception {
        fetchReturns(
                Set.of(20L),
                workPackage(20, "Task", List.of(href(UNDISCLOSED), workPackageLink(10)), List.of(href(UNDISCLOSED))));
        when(fetcher.fetchWorkPackages(List.of(10L))).thenReturn(List.of(workPackage(10, "Epic")));

        List<JsonNode> added = collector.collect(List.of(20L), run);

        assertEquals(List.of(20L, 10L), numbersOf(added));
        verify(fetcher).fetchWorkPackages(List.of(20L));
        verify(fetcher).fetchWorkPackages(List.of(10L));
        verifyNoMoreInteractions(fetcher);
    }
}
