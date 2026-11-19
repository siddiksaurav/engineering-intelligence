package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dsinnovators.ei.common.CanonicalJson;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.JsonNode;

class WorkPackageFetcherTest {

    private static final String API = OpenProjectApiFixture.API_URL;

    private final OpenProjectApiFixture fixture = new OpenProjectApiFixture(2);
    private final WorkPackageFetcher fetcher = new WorkPackageFetcher(fixture.api, fixture.properties);

    private static String recorded(String name) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("openproject/" + name).getInputStream(), StandardCharsets.UTF_8);
    }

    private static String filteredUrl(String filterName, String values, int pageSize) {
        String filters = "[{\"" + filterName + "\":{\"operator\":\"=\",\"values\":[" + values + "]}}]";
        return API + "/work_packages?filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8) + "&pageSize="
                + pageSize;
    }

    private void expectGet(String url, String recordedName) throws IOException {
        fixture.server
                .expect(requestTo(URI.create(url)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(recorded(recordedName), MediaType.APPLICATION_JSON));
    }

    @Test
    void readsEveryPageOfTheTreeUnderAFeature() throws Exception {
        String treeUrl = filteredUrl("ancestor", "\"69622\"", 2);
        expectGet(treeUrl, "work-packages-tree-page-1.json");
        expectGet(treeUrl + "&offset=2", "work-packages-tree-page-2.json");

        List<JsonNode> tree = fetcher.fetchTreeUnder(69622);

        assertEquals(
                List.of(83452L, 83453L, 83460L),
                tree.stream().map(element -> element.path("id").asLong()).toList());
        fixture.server.verify();
    }

    @Test
    void fetchesWorkPackagesByNumberInCallsOfOnePageOfNumbers() throws Exception {
        expectGet(filteredUrl("id", "\"81224\",\"84000\"", 2), "work-packages-81224-and-84000.json");
        expectGet(filteredUrl("id", "\"56343\"", 2), "work-packages-56343.json");

        List<JsonNode> workPackages = fetcher.fetchWorkPackages(List.of(81224L, 84000L, 56343L, 81224L));

        assertEquals(3, workPackages.size());
        fixture.server.verify();
    }

    @Test
    void fetchesTheActivitiesAndTheReducedPullRequestListOfAWorkPackage() throws Exception {
        JsonNode workPackage = CanonicalJson.parse(recorded("work-package-81224.json"));
        expectGet(API + "/work_packages/81224/activities", "activities-81224.json");
        expectGet(API + "/work_packages/81224/github_pull_requests", "github-pull-requests-81224.json");

        FetchedWorkPackage fetched = fetcher.fetchWithDetails(workPackage);

        assertEquals(81224, fetched.number());
        assertEquals(workPackage, fetched.workPackage());
        assertEquals(48, fetched.activities().path("total").asInt());
        assertTrue(fetched.pullRequestList().path("reduced").asBoolean());
        assertEquals(2, fetched.pullRequestList().path("pullRequests").size());
    }

    @Test
    void fetchesAProjectAsReturned() throws Exception {
        expectGet(API + "/projects/1395", "project-1395.json");

        JsonNode project = fetcher.fetchProject(1395);

        assertEquals("COMMS", project.path("identifier").asString());
    }

    @Test
    void refusesACollectionWithoutATotal() {
        fixture.server
                .expect(requestTo(URI.create(filteredUrl("ancestor", "\"1\"", 2))))
                .andRespond(withSuccess("{\"_embedded\":{\"elements\":[]}}", MediaType.APPLICATION_JSON));

        assertThrows(OpenProjectApiException.class, () -> fetcher.fetchTreeUnder(1));
    }

    private void expectTreeElements(String elements) {
        fixture.server
                .expect(requestTo(URI.create(filteredUrl("ancestor", "\"1\"", 2))))
                .andRespond(withSuccess(
                        "{\"total\":1,\"_embedded\":{\"elements\":[" + elements + "]}}", MediaType.APPLICATION_JSON));
    }

    private OpenProjectApiException refusedTree(String elements) {
        expectTreeElements(elements);
        return assertThrows(OpenProjectApiException.class, () -> fetcher.fetchTreeUnder(1));
    }

    @Test
    void refusesAnElementWithoutAnIdNamingTheCallAndTheField() {
        OpenProjectApiException failure =
                refusedTree("{\"_type\":\"WorkPackage\",\"_links\":{\"self\":{\"href\":\"/x\"}}}");

        assertTrue(failure.getMessage().contains("/work_packages?filters="), failure.getMessage());
        assertTrue(failure.getMessage().contains("\"id\""), failure.getMessage());
    }

    @Test
    void refusesAnElementWithoutLinks() {
        OpenProjectApiException failure = refusedTree("{\"_type\":\"WorkPackage\",\"id\":5}");

        assertTrue(failure.getMessage().contains("\"_links\""), failure.getMessage());
    }

    @Test
    void refusesAnElementOfTheWrongType() {
        OpenProjectApiException failure =
                refusedTree("{\"_type\":\"Project\",\"id\":5,\"_links\":{\"self\":{\"href\":\"/x\"}}}");

        assertTrue(failure.getMessage().contains("\"_type\""), failure.getMessage());
    }

    @Test
    void refusesActivitiesWhoseElementsAreNotAList() throws Exception {
        JsonNode workPackage = CanonicalJson.parse(recorded("work-package-81224.json"));
        fixture.server
                .expect(requestTo(URI.create(API + "/work_packages/81224/activities")))
                .andRespond(withSuccess("{\"_embedded\":{\"elements\":{}}}", MediaType.APPLICATION_JSON));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> fetcher.fetchWithDetails(workPackage));

        assertTrue(failure.getMessage().contains("work package 81224"), failure.getMessage());
        assertTrue(failure.getMessage().contains("_embedded.elements"), failure.getMessage());
    }
}
