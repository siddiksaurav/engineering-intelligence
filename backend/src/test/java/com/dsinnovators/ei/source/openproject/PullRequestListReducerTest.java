package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.common.CanonicalJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.JsonNode;

class PullRequestListReducerTest {

    private static final List<String> KEPT_FIELDS = List.of(
            "id",
            "number",
            "repository",
            "htmlUrl",
            "title",
            "state",
            "draft",
            "merged",
            "mergedAt",
            "createdAt",
            "githubUser");

    private static JsonNode recordedList() throws IOException {
        return CanonicalJson.parse(StreamUtils.copyToString(
                new ClassPathResource("openproject/github-pull-requests-81224.json").getInputStream(),
                StandardCharsets.UTF_8));
    }

    @Test
    void keepsOnlyTheStableFieldsAndMarksTheCopyReduced() throws IOException {
        JsonNode source = recordedList();

        JsonNode reduced = PullRequestListReducer.reduce(source);

        assertTrue(reduced.path("reduced").asBoolean());
        JsonNode entries = reduced.path("pullRequests");
        assertEquals(2, entries.size());
        JsonNode firstSource = source.path("_embedded").path("elements").get(0);
        JsonNode first = entries.get(0);
        assertEquals(KEPT_FIELDS, first.propertyNames().stream().sorted(order()).toList());
        assertEquals(firstSource.path("number"), first.path("number"));
        assertEquals(firstSource.path("htmlUrl"), first.path("htmlUrl"));
        assertEquals(firstSource.path("mergedAt"), first.path("mergedAt"));
        assertEquals("akabiru", first.path("githubUser").asString());
    }

    @Test
    void keepsTheOrderOpenProjectReturned() throws IOException {
        JsonNode source = recordedList();

        JsonNode entries = PullRequestListReducer.reduce(source).path("pullRequests");

        for (int index = 0; index < entries.size(); index++) {
            assertEquals(
                    source.path("_embedded").path("elements").get(index).path("id"),
                    entries.get(index).path("id"));
        }
    }

    @Test
    void refusesAnAnswerWithoutAListOfElements() {
        JsonNode answer = CanonicalJson.parse("{\"_type\":\"Collection\"}");

        assertThrows(OpenProjectApiException.class, () -> PullRequestListReducer.reduce(answer));
    }

    private static Comparator<String> order() {
        return Comparator.comparingInt(KEPT_FIELDS::indexOf);
    }

    private static OpenProjectApiException refusedEntry(String entry) {
        JsonNode answer = CanonicalJson.parse("{\"_embedded\":{\"elements\":[" + entry + "]}}");
        return assertThrows(OpenProjectApiException.class, () -> PullRequestListReducer.reduce(answer));
    }

    @Test
    void refusesAnEntryWithoutANumber() {
        OpenProjectApiException failure = refusedEntry("{\"id\":7,\"repository\":\"opf/openproject\"}");

        assertTrue(failure.getMessage().contains("\"number\""), failure.getMessage());
    }

    @Test
    void refusesAnEntryWithoutARepository() {
        OpenProjectApiException failure = refusedEntry("{\"id\":7,\"number\":24204,\"repository\":\" \"}");

        assertTrue(failure.getMessage().contains("\"repository\""), failure.getMessage());
    }
}
