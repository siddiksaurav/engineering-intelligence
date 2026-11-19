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

class ProjectRecorderTest extends DatabaseTest {

    @Autowired
    private ProjectRecorder recorder;

    private static JsonNode project(String identifier, String name) {
        return CanonicalJson.parse(
                "{\"_type\":\"Project\",\"id\":1395,\"identifier\":\"" + identifier + "\",\"name\":\"" + name + "\"}");
    }

    private List<Map<String, Object>> projectRows() {
        return jdbc.queryForList("select external_id, identifier, name, url from project");
    }

    @Test
    void storesTheRawRecordAndFillsTheProjectRow() {
        StoredRecord stored = recorder.store(project("comms", "Communications"));

        assertEquals(new StoredRecord(OpenProjectRecordKind.PROJECT, Outcome.CREATED), stored);
        assertEquals(
                List.of(Map.of(
                        "external_id",
                        "1395",
                        "identifier",
                        "comms",
                        "name",
                        "Communications",
                        "url",
                        "https://community.openproject.org/projects/comms")),
                projectRows());
        assertEquals(
                List.of(Map.of(
                        "record_kind",
                        "project",
                        "external_id",
                        "1395",
                        "url",
                        "https://community.openproject.org/projects/comms")),
                jdbc.queryForList("select record_kind, external_id, url from raw_record"));
    }

    @Test
    void aChangedNameOrIdentifierUpdatesTheRowInsteadOfAddingOne() {
        recorder.store(project("comms", "Communications"));

        StoredRecord stored = recorder.store(project("comm", "Comms team"));

        assertEquals(Outcome.NEW_VERSION, stored.outcome());
        assertEquals(
                List.of(Map.of(
                        "external_id",
                        "1395",
                        "identifier",
                        "comm",
                        "name",
                        "Comms team",
                        "url",
                        "https://community.openproject.org/projects/comm")),
                projectRows());
    }

    @Test
    void aProjectWithoutAnIdentifierIsRefusedAndNothingIsStored() {
        JsonNode broken = CanonicalJson.parse("{\"_type\":\"Project\",\"id\":1395,\"name\":\"Communications\"}");

        OpenProjectApiException failure = assertThrows(OpenProjectApiException.class, () -> recorder.store(broken));

        assertEquals("The answer for project 1395 has no identifier", failure.getMessage());
        assertEquals(List.of(), projectRows());
    }

    @Test
    void anAnswerThatIsNotAProjectIsRefusedAndNothingIsStored() {
        JsonNode notAProject = CanonicalJson.parse(
                "{\"_type\":\"Error\",\"id\":1395,\"identifier\":\"comms\",\"name\":\"Communications\"}");

        OpenProjectApiException failure =
                assertThrows(OpenProjectApiException.class, () -> recorder.store(notAProject));

        assertEquals("The answer for project 1395 is not of the kind Project", failure.getMessage());
        assertEquals(List.of(), projectRows());
    }
}
