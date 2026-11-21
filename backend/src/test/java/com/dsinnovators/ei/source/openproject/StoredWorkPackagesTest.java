package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.source.openproject.PullRequestWorkItemLinks.Found;
import com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class StoredWorkPackagesTest extends DatabaseTest {

    private static final Quarter Q3 = Quarter.parse("2026-Q3");

    @Autowired
    private StoredWorkPackages stored;

    @Autowired
    private WorkPackageRecorder recorder;

    @Autowired
    private PullRequestWorkItemLinks links;

    private static JsonNode json(String text) {
        return CanonicalJson.parse(text);
    }

    private void store(long number, String type, Long parent, String... activities) {
        String parentLink = parent == null ? "{\"href\":null}" : "{\"href\":\"/api/v3/work_packages/" + parent + "\"}";
        recorder.store(new FetchedWorkPackage(
                number,
                json("{\"id\":" + number + ",\"_links\":{\"type\":{\"title\":\"" + type + "\"},\"parent\":" + parentLink
                        + "}}"),
                json("{\"_embedded\":{\"elements\":[" + String.join(",", activities) + "]}}"),
                json("{\"reduced\":true,\"pullRequests\":[]}")));
    }

    private static String statusChange(long id, long number, String from, String to, String createdAt, long user) {
        return "{\"id\":" + id + ",\"createdAt\":\"" + createdAt + "\",\"details\":[{\"raw\":\"Status changed from "
                + from + " to " + to + "\",\"html\":\"<strong>Status</strong> changed from <i>" + from + "</i> to <i>"
                + to + "</i>\"}],\"_links\":{\"workPackage\":{\"href\":\"/api/v3/work_packages/" + number
                + "\"},\"user\":{\"href\":\"/api/v3/users/" + user + "\"}}}";
    }

    private void storeFeatureChangedInto(long number, String status, String createdAt) {
        store(number, "Feature", null, statusChange(number * 10, number, "new", status, createdAt, 7));
    }

    @Test
    void aWorkPackageIsFoundByItsNumber() {
        store(81224, "Feature", null);

        assertEquals(
                81224, stored.find(81224).orElseThrow().getPayload().path("id").asLong());
        assertEquals(Optional.empty(), stored.find(1));
    }

    @Test
    void theLinksOfAPullRequestAreReadBack() {
        links.replace(
                "opf/openproject#1",
                List.of(new Found(ResolvedReference.resolved("81224", 81224), FoundIn.OPENPROJECT)));

        List<PullRequestWorkItemLink> found = stored.linksOf("opf/openproject#1");

        assertEquals(1, found.size());
        assertEquals(81224L, found.get(0).getWorkPackageId());
        assertEquals(FoundIn.OPENPROJECT, found.get(0).getFoundIn());
        assertTrue(found.get(0).isVisible());
        assertEquals(List.of(), stored.linksOf("opf/openproject#2"));
    }

    @Test
    void theNearestFeatureIsTheItemItselfOrItsNearestFeatureAncestor() {
        store(1, "Epic", null);
        store(2, "Feature", 1L);
        store(3, "Feature", 2L);
        store(4, "Task", 3L);
        store(5, "Bug", 2L);
        store(6, "Task", 1L);
        store(7, "Task", 99L);

        assertEquals(Optional.of(3L), stored.nearestFeature(3));
        assertEquals(Optional.of(3L), stored.nearestFeature(4));
        assertEquals(Optional.of(2L), stored.nearestFeature(5));
        assertEquals(Optional.empty(), stored.nearestFeature(6));
        assertEquals(Optional.empty(), stored.nearestFeature(7));
        assertEquals(Optional.empty(), stored.nearestFeature(404));
    }

    @Test
    void theStatusChangesAreReadFromTheActivitiesOfThatWorkPackageOldestFirst() {
        store(
                10,
                "Feature",
                null,
                statusChange(2, 10, "in development", "tested", "2026-09-30T10:00:00.000Z", 8),
                statusChange(1, 10, "new", "in development", "2026-09-01T10:00:00.000Z", 7),
                "{\"id\":3,\"createdAt\":\"2026-09-02T10:00:00.000Z\",\"details\":[{\"raw\":\"Priority set to High\","
                        + "\"html\":\"<strong>Priority</strong> set to <i>High</i>\"}],\"_links\":{\"workPackage\":{\"href\":\"/api/v3/work_packages/10\"}}}");
        storeFeatureChangedInto(11, "tested", "2026-09-05T10:00:00.000Z");

        assertEquals(
                List.of(
                        new StatusChange("in development", Instant.parse("2026-09-01T10:00:00Z"), 7L),
                        new StatusChange("tested", Instant.parse("2026-09-30T10:00:00Z"), 8L)),
                stored.statusChanges(10));
    }

    @Test
    void aFeatureIsDoneInTheQuarterWhenItsStatusChangedIntoADoneStatusInIt() {
        storeFeatureChangedInto(20, "tested", "2026-09-30T23:59:59.000Z");
        storeFeatureChangedInto(21, "tested", "2026-10-01T00:00:00.000Z");
        storeFeatureChangedInto(22, "rejected", "2026-09-15T10:00:00.000Z");
        storeFeatureChangedInto(23, "Closed", "2026-08-15T10:00:00.000Z");
        storeFeatureChangedInto(25, "closed", "2026-08-15T10:00:00.000Z");
        store(24, "Feature", null);

        assertTrue(stored.isDoneIn(20, Q3));
        assertFalse(stored.isDoneIn(21, Q3));
        assertFalse(stored.isDoneIn(22, Q3));
        assertFalse(stored.isDoneIn(23, Q3), "a status name is matched exactly, keeping its case");
        assertTrue(stored.isDoneIn(25, Q3));
        assertFalse(stored.isDoneIn(24, Q3));
    }

    @Test
    void aStatusSetToADoneStatusInTheQuarterAlsoMakesTheFeatureDone() {
        store(
                30,
                "Feature",
                null,
                "{\"id\":300,\"createdAt\":\"2026-08-01T10:00:00.000Z\",\"details\":[{\"raw\":\"Status set to closed\","
                        + "\"html\":\"<strong>Status</strong> set to <i>closed</i>\"}],\"_links\":{\"workPackage\":{\"href\":\"/api/v3/work_packages/30\"}}}");

        assertTrue(stored.isDoneIn(30, Q3));
        assertEquals(
                List.of(new StatusChange("closed", Instant.parse("2026-08-01T10:00:00Z"), null)),
                stored.statusChanges(30));
    }

    @Test
    void theWalkUpEndsAtAParentThatTheApiHides() {
        recorder.store(new FetchedWorkPackage(
                8,
                json("{\"id\":8,\"_links\":{\"type\":{\"title\":\"Task\"},\"parent\":{\"href\":"
                        + "\"urn:openproject-org:api:v3:undisclosed\"}}}"),
                json("{\"_embedded\":{\"elements\":[]}}"),
                json("{\"reduced\":true,\"pullRequests\":[]}")));

        assertEquals(Optional.empty(), stored.nearestFeature(8));
    }

    private void storeWithActivities(long number, String type, String... activities) {
        store(number, type, null, activities);
    }

    @Test
    void firstDoneAtIsTheFirstChangeIntoADoneStatus() {
        storeWithActivities(
                40,
                "Feature",
                statusChange(1, 40, "new", "in development", "2026-08-01T10:00:00.000Z", 7),
                statusChange(3, 40, "tested", "closed", "2026-09-20T10:00:00.000Z", 7),
                statusChange(2, 40, "in development", "tested", "2026-09-10T08:30:00.000Z", 7));
        storeWithActivities(41, "Feature", statusChange(4, 41, "new", "in development", "2026-08-01T10:00:00.000Z", 7));
        storeWithActivities(42, "Feature");

        assertEquals(Optional.of(Instant.parse("2026-09-10T08:30:00Z")), stored.firstDoneAt(40, Q3));
        assertEquals(Optional.empty(), stored.firstDoneAt(41, Q3));
        assertEquals(Optional.empty(), stored.firstDoneAt(42, Q3));
    }

    @Test
    void firstDoneAtChecksAChangeFrom2019AgainstTheGivenQuartersStatuses() {
        storeWithActivities(
                50,
                "Feature",
                statusChange(1, 50, "new", "closed", "2019-03-04T10:00:00.000Z", 7),
                statusChange(2, 50, "closed", "in development", "2019-05-04T10:00:00.000Z", 7));

        assertEquals(Optional.of(Instant.parse("2019-03-04T10:00:00Z")), stored.firstDoneAt(50, Q3));
    }

    @Test
    void theFactsOfAWorkPackageAreReadFromItsRecordAndItsProject() {
        jdbc.update("""
                insert into project (data_source_id, external_id, identifier, name, url)
                values ((select id from data_source where key = 'openproject'), '1395', 'comms', 'Comms',
                        'https://community.openproject.org/projects/comms')
                """);
        Long projectId = jdbc.queryForObject("select id from project", Long.class);
        recorder.store(new FetchedWorkPackage(
                81224,
                json("{\"id\":81224,\"subject\":\"Notify desktop users\",\"_links\":{"
                        + "\"type\":{\"title\":\"Bug\"},\"priority\":{\"title\":\"High\"},"
                        + "\"project\":{\"href\":\"/api/v3/projects/1395\"}}}"),
                json("{\"_embedded\":{\"elements\":[]}}"),
                json("{\"reduced\":true,\"pullRequests\":[]}")));
        Long recordId = jdbc.queryForObject("select id from raw_record where record_kind = 'work_package'", Long.class);

        WorkPackageFacts facts = stored.facts(81224).orElseThrow();

        assertEquals(
                new WorkPackageFacts(
                        recordId,
                        81224,
                        "Notify desktop users",
                        "Bug",
                        true,
                        projectId,
                        "High",
                        "https://community.openproject.org/wp/81224"),
                facts);
        assertEquals(Optional.empty(), stored.facts(1));
    }

    @Test
    void aFeatureIsNotABugAndAProjectThatIsNotStoredGivesNoProjectId() {
        store(60, "Feature", null);

        WorkPackageFacts facts = stored.facts(60).orElseThrow();

        assertFalse(facts.isBug());
        assertEquals(null, facts.projectId());
        assertEquals(null, facts.priority());
    }

    @Test
    void theTextOfAWorkPackageHoldsTitleTypePriorityParentDescriptionAndLink() {
        recorder.store(new FetchedWorkPackage(
                81224,
                json("{\"id\":81224,\"subject\":\"Notify desktop users\","
                        + "\"description\":{\"format\":\"markdown\",\"raw\":\"# Problem\\nUsers miss alerts.\"},"
                        + "\"_links\":{\"type\":{\"title\":\"Feature\"},\"priority\":{\"title\":\"High\"},"
                        + "\"parent\":{\"href\":\"/api/v3/work_packages/70725\",\"title\":\"Admin section\"}}}"),
                json("{\"_embedded\":{\"elements\":[]}}"),
                json("{\"reduced\":true,\"pullRequests\":[]}")));

        WorkPackageText text = stored.text(81224).orElseThrow();

        assertEquals(
                new WorkPackageText(
                        81224,
                        "Notify desktop users",
                        "Feature",
                        Optional.of("High"),
                        Optional.of("Admin section"),
                        "# Problem\nUsers miss alerts.",
                        "https://community.openproject.org/wp/81224"),
                text);
        assertEquals(Optional.empty(), stored.text(1));
    }

    @Test
    void aWorkPackageWithoutPriorityParentOrDescriptionHasEmptyParts() {
        store(60, "Feature", null);

        WorkPackageText text = stored.text(60).orElseThrow();

        assertEquals(Optional.empty(), text.priority());
        assertEquals(Optional.empty(), text.parentTitle());
        assertEquals("", text.description());
    }
}
