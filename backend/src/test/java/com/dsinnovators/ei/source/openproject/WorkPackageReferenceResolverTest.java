package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.util.StreamUtils;

class WorkPackageReferenceResolverTest {

    private static final String WORK_PACKAGES = OpenProjectApiFixture.API_URL + "/work_packages";

    private final OpenProjectApiFixture fixture = new OpenProjectApiFixture();
    private final WorkPackageReferenceResolver resolver =
            new WorkPackageReferenceResolver(fixture.api, fixture.properties);

    private static String recorded(String name) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource("openproject/" + name).getInputStream(), StandardCharsets.UTF_8);
    }

    private static DefaultResponseCreator json(String name) throws IOException {
        return withSuccess(recorded(name), MediaType.APPLICATION_JSON);
    }

    private static DefaultResponseCreator error(HttpStatus status, String name) throws IOException {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON).body(recorded(name));
    }

    private static String collectionUrl(int pageSize, String... references) {
        String values = String.join(
                ",",
                Arrays.stream(references)
                        .map(reference -> "\"" + reference + "\"")
                        .toList());
        String filters = "[{\"id\":{\"operator\":\"=\",\"values\":[" + values + "]}}]";
        return WORK_PACKAGES + "?filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8) + "&pageSize="
                + pageSize;
    }

    private void expectGet(String url, DefaultResponseCreator answer) {
        fixture.server
                .expect(requestTo(URI.create(url)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(answer);
    }

    @Test
    void resolvesTheNumberTheKeyAndAnOldKeyToTheSameWorkPackage() throws Exception {
        expectGet(collectionUrl(100, "81224", "COMMS-1078", "SE-694"), json("work-packages-81224-by-id-and-keys.json"));
        expectGet(WORK_PACKAGES + "/SE-694", json("work-package-81224.json"));

        List<ResolvedReference> resolved = resolver.resolve(List.of("81224", "COMMS-1078", "SE-694"));

        assertEquals(
                List.of(
                        ResolvedReference.resolved("81224", 81224),
                        ResolvedReference.resolved("COMMS-1078", 81224),
                        ResolvedReference.resolved("SE-694", 81224)),
                resolved);
        fixture.server.verify();
    }

    @Test
    void repeatsAfterABadRequestOneReferenceAtATimeAndMarksANotFoundOneNotVisible() throws Exception {
        expectGet(collectionUrl(100, "81224", "DEV-1"), error(HttpStatus.BAD_REQUEST, "work-packages-bad-filter.json"));
        expectGet(WORK_PACKAGES + "/81224", json("work-package-81224.json"));
        expectGet(WORK_PACKAGES + "/DEV-1", error(HttpStatus.NOT_FOUND, "work-package-not-found.json"));

        List<ResolvedReference> resolved = resolver.resolve(List.of("81224", "DEV-1"));

        assertEquals(
                List.of(ResolvedReference.resolved("81224", 81224), ResolvedReference.notVisible("DEV-1")), resolved);
        assertFalse(resolved.get(1).visible());
        fixture.server.verify();
    }

    @Test
    void marksAnUnmatchedKeyThatTheServerDoesNotKnowNotVisible() throws Exception {
        expectGet(collectionUrl(100, "81224", "SC-9"), json("work-packages-81224-by-id-and-keys.json"));
        expectGet(WORK_PACKAGES + "/SC-9", error(HttpStatus.NOT_FOUND, "work-package-not-found.json"));

        List<ResolvedReference> resolved = resolver.resolve(List.of("81224", "SC-9"));

        assertEquals(
                List.of(ResolvedReference.resolved("81224", 81224), ResolvedReference.notVisible("SC-9")), resolved);
        fixture.server.verify();
    }

    @Test
    void splitsReferencesIntoCollectionCallsOfTheConfiguredPageSizeAndAsksOnceForADuplicate() throws Exception {
        OpenProjectApiFixture small = new OpenProjectApiFixture(2);
        WorkPackageReferenceResolver smallResolver = new WorkPackageReferenceResolver(small.api, small.properties);
        small.server
                .expect(requestTo(URI.create(collectionUrl(2, "81224", "COMMS-1100"))))
                .andRespond(json("work-packages-81224-and-84000.json"));
        small.server
                .expect(requestTo(URI.create(collectionUrl(2, "56343"))))
                .andRespond(json("work-packages-56343.json"));

        List<ResolvedReference> resolved = smallResolver.resolve(List.of("81224", "COMMS-1100", "81224", "56343"));

        assertEquals(
                List.of(
                        ResolvedReference.resolved("81224", 81224),
                        ResolvedReference.resolved("COMMS-1100", 84000),
                        ResolvedReference.resolved("56343", 56343)),
                resolved);
        small.server.verify();
    }

    @Test
    void failsNamingTheCallWhenAnElementHasNoNumber() {
        expectGet(
                collectionUrl(100, "81224"),
                withSuccess(
                        "{\"_embedded\":{\"elements\":[{\"displayId\":\"COMMS-1078\"}]}}", MediaType.APPLICATION_JSON));

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> resolver.resolve(List.of("81224")));

        assertEquals(
                "OpenProject's answer for an element of the work package collection call has no numeric id",
                failure.getMessage());
    }

    @Test
    void resolvesNothingWithoutACall() throws Exception {
        assertEquals(List.of(), resolver.resolve(List.of()));
        fixture.server.verify();
    }
}
