package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.StoreResult.Outcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class WorkPackageRecorderTest extends DatabaseTest {

    private static final String BASE = "https://community.openproject.org/work_packages/81224";

    @Autowired
    private WorkPackageRecorder recorder;

    private static JsonNode json(String text) {
        return CanonicalJson.parse(text);
    }

    private static FetchedWorkPackage workPackage(String activities) {
        return new FetchedWorkPackage(
                81224,
                json("{\"id\":81224,\"subject\":\"Subject\"}"),
                json("{\"_embedded\":{\"elements\":" + activities + "}}"),
                json("{\"reduced\":true,\"pullRequests\":[]}"));
    }

    private static final String TWO_ACTIVITIES = "[{\"id\":1756926,\"version\":1},{\"id\":1756927,\"version\":2}]";

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList(
                "select record_kind, external_id, version, url from raw_record order by record_kind, external_id, version");
    }

    private static Map<String, Object> row(String kind, String externalId, String url) {
        return Map.of("record_kind", kind, "external_id", externalId, "version", 1, "url", url);
    }

    @Test
    void storesAWorkPackageWithItsActivitiesAndPullRequestListOrNothing() {
        FetchedWorkPackage withBrokenActivity = workPackage("[{\"id\":1756926},{\"version\":2}]");

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> recorder.store(withBrokenActivity));

        assertEquals("An activity of work package 81224 has no id: {\"version\":2}", failure.getMessage());
        assertEquals(List.of(), rows());
    }

    @Test
    void activitiesThatAreNotAListAreRefusedAndNothingIsStored() {
        FetchedWorkPackage withBrokenActivities = workPackage("{}");

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> recorder.store(withBrokenActivities));

        assertEquals(
                "OpenProject's answer to the activities of work package 81224 has no list in \"_embedded.elements\"",
                failure.getMessage());
        assertEquals(List.of(), rows());
    }

    @Test
    void storesOneRowPerRecordWithTheUrlsOfTheDesign() {
        List<StoredRecord> stored = recorder.store(workPackage(TWO_ACTIVITIES));

        assertEquals(
                List.of(
                        row("work_package", "81224", BASE),
                        row("work_package_activity", "1756926", BASE + "/activity"),
                        row("work_package_activity", "1756927", BASE + "/activity"),
                        row("work_package_pull_requests", "81224", BASE + "/tabs/github")),
                rows());
        assertEquals(
                List.of(
                        OpenProjectRecordKind.WORK_PACKAGE,
                        OpenProjectRecordKind.WORK_PACKAGE_ACTIVITY,
                        OpenProjectRecordKind.WORK_PACKAGE_ACTIVITY,
                        OpenProjectRecordKind.WORK_PACKAGE_PULL_REQUESTS),
                stored.stream().map(StoredRecord::kind).toList());
        assertEquals(
                List.of(Outcome.CREATED),
                stored.stream().map(StoredRecord::outcome).distinct().toList());
    }

    @Test
    void aWorkPackageStoredAgainIsUnchangedAndAChangeIsANewVersion() {
        recorder.store(workPackage(TWO_ACTIVITIES));

        List<StoredRecord> again = recorder.store(workPackage(TWO_ACTIVITIES));
        List<StoredRecord> changed = recorder.store(new FetchedWorkPackage(
                81224,
                json("{\"id\":81224,\"subject\":\"Edited\"}"),
                json("{\"_embedded\":{\"elements\":" + TWO_ACTIVITIES + "}}"),
                json("{\"reduced\":true,\"pullRequests\":[]}")));

        assertEquals(
                List.of(Outcome.UNCHANGED),
                again.stream().map(StoredRecord::outcome).distinct().toList());
        assertEquals(Outcome.NEW_VERSION, changed.get(0).outcome());
        assertEquals(5, rows().size());
    }
}
