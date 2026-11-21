package com.dsinnovators.ei.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.identity.EvidenceEntry.Signal;
import com.dsinnovators.ei.source.RawRecordStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OpenProjectCandidatesTest extends DatabaseTest {

    private static final String PULL_REQUEST_1 = "opf/openproject#1";
    private static final String PULL_REQUEST_2 = "opf/openproject#2";
    private static final long KABIRU = 80250L;
    private static final long BRUNO = 99165L;
    private static final long TEAM = 99377L;
    private static final long QA_USER = 83297L;

    @Autowired
    private RawRecordStore store;

    @Autowired
    private OpenProjectCandidates candidates;

    private static String user(long id, String name) {
        return "{\"title\":\"%s\",\"href\":\"/api/v3/users/%d\"}".formatted(name, id);
    }

    private static String group(long id, String name) {
        return "{\"title\":\"%s\",\"href\":\"/api/v3/groups/%d\"}".formatted(name, id);
    }

    private void storeWorkItem(long number, String... developers) {
        store.store(
                "openproject",
                "work_package",
                Long.toString(number),
                "https://community.openproject.org/api/v3/work_packages/" + number,
                CanonicalJson.parse("{\"id\":%d,\"_links\":{\"customField270\":[%s]}}"
                        .formatted(number, String.join(",", developers))));
    }

    private void storeStatusChange(long activityId, long number, String to, String createdAt, long userId) {
        String payload =
                ("{\"id\":%d,\"createdAt\":\"%s\",\"details\":[{\"html\":\"<strong>Status</strong> changed from"
                                + " <i>new</i> to <i>%s</i>\"}],\"_links\":{\"workPackage\":{\"href\":\"/api/v3/work_packages/%d\"},"
                                + "\"user\":{\"href\":\"/api/v3/users/%d\"}}}")
                        .formatted(activityId, createdAt, to, number, userId);
        store.store(
                "openproject",
                "work_package_activity",
                Long.toString(activityId),
                "https://community.openproject.org/api/v3/activities/" + activityId,
                CanonicalJson.parse(payload));
    }

    private void link(String pullRequest, String reference, Long workPackage, String state) {
        jdbc.update("""
                insert into pull_request_work_item (pull_request_external_id, work_package_id, reference, found_in, state)
                values (?, ?, ?, 'openproject', ?)
                """, pullRequest, workPackage, reference, state);
    }

    @Test
    void countsDevelopersAndStatusChangesPerUserAndIgnoresGroups() {
        storeWorkItem(100, user(KABIRU, "Kabiru Mwenja"), user(BRUNO, "Bruno Pagno"), group(TEAM, "OP Dev Team"));
        storeWorkItem(101, user(KABIRU, "Kabiru Mwenja"));
        storeWorkItem(102);
        storeStatusChange(1, 100, "in development", "2026-07-02T10:00:00.000Z", KABIRU);
        storeStatusChange(2, 100, "tested", "2026-07-03T10:00:00.000Z", KABIRU);
        storeStatusChange(3, 102, "tested", "2026-07-04T10:00:00.000Z", QA_USER);
        link(PULL_REQUEST_1, "100", 100L, "resolved");
        link(PULL_REQUEST_1, "101", 101L, "resolved");
        link(PULL_REQUEST_2, "100", 100L, "resolved");
        link(PULL_REQUEST_2, "102", 102L, "resolved");
        link(PULL_REQUEST_2, "HIDDEN-1", null, "not_visible");

        List<Candidate> found = candidates.of(List.of(PULL_REQUEST_1, PULL_REQUEST_2));

        assertEquals(
                List.of(KABIRU, BRUNO), found.stream().map(Candidate::userId).toList());
        Candidate bruno = found.get(1);
        assertEquals("Bruno Pagno", bruno.name());
        assertEquals(1, bruno.supportingItems());
        assertEquals(0, bruno.statusChangeItems());
        assertEquals(3, bruno.personItems());
        Candidate kabiru = found.get(0);
        assertEquals("Kabiru Mwenja", kabiru.name());
        assertEquals(2, kabiru.supportingItems(), "a work item linked twice counts once");
        assertEquals(1, kabiru.statusChangeItems(), "two changes on one item count as one item");
        assertEquals(
                List.of(
                        new EvidenceEntry(100, "https://community.openproject.org/wp/100", Signal.DEVELOPERS, null),
                        new EvidenceEntry(101, "https://community.openproject.org/wp/101", Signal.DEVELOPERS, null),
                        new EvidenceEntry(
                                100,
                                "https://community.openproject.org/wp/100",
                                Signal.STATUS_CHANGE,
                                Instant.parse("2026-07-02T10:00:00Z")),
                        new EvidenceEntry(
                                100,
                                "https://community.openproject.org/wp/100",
                                Signal.STATUS_CHANGE,
                                Instant.parse("2026-07-03T10:00:00Z"))),
                kabiru.evidence());
    }

    @Test
    void aUserSeenOnlyInStatusChangesIsNotACandidate() {
        storeWorkItem(100, user(KABIRU, "Kabiru Mwenja"));
        storeStatusChange(1, 100, "tested", "2026-07-03T10:00:00.000Z", QA_USER);
        link(PULL_REQUEST_1, "100", 100L, "resolved");

        assertEquals(
                List.of(KABIRU),
                candidates.of(List.of(PULL_REQUEST_1)).stream()
                        .map(Candidate::userId)
                        .toList());
    }

    @Test
    void noLinkedWorkItemGivesNoCandidate() {
        assertTrue(candidates.of(List.of(PULL_REQUEST_1)).isEmpty());
        assertTrue(candidates.of(List.of()).isEmpty());
    }

    @Test
    void aLinkedWorkItemThatWasNotStoredCountsForThePersonButGivesNoCandidate() {
        storeWorkItem(100, user(KABIRU, "Kabiru Mwenja"));
        link(PULL_REQUEST_1, "100", 100L, "resolved");
        link(PULL_REQUEST_1, "999", 999L, "resolved");

        Candidate kabiru = candidates.of(List.of(PULL_REQUEST_1)).get(0);

        assertEquals(2, kabiru.personItems());
        assertEquals(1, kabiru.supportingItems());
    }
}
