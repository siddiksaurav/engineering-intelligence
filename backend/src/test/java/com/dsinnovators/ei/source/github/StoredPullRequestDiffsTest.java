package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecordStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class StoredPullRequestDiffsTest extends DatabaseTest {

    private static final String EXTERNAL_ID = "opf/openproject#7";

    @Autowired
    private RawRecordStore store;

    @Autowired
    private StoredPullRequestDiffs storedDiffs;

    private long storeDiff(String externalId, String filesJson) {
        return store.store(
                        PullRequestRecorder.DATA_SOURCE,
                        GitHubRecordKind.PULL_REQUEST_DIFF.recordKind(),
                        externalId,
                        "https://github.com/opf/openproject/pull/7/files",
                        CanonicalJson.parse("{\"files\":" + filesJson + ",\"truncated\":false}"))
                .record()
                .getId();
    }

    @Test
    void readsTheFilesOfTheLatestDiffVersion() {
        storeDiff(EXTERNAL_ID, "[{\"filename\":\"old.rb\",\"status\":\"added\",\"patch\":\"+old\"}]");
        long latestId = storeDiff(
                EXTERNAL_ID,
                "[{\"filename\":\"app/a.rb\",\"status\":\"modified\",\"additions\":3,\"patch\":\"@@ -1 +1 @@\"},"
                        + "{\"filename\":\"logo.png\",\"status\":\"added\"}]");

        PullRequestDiff diff = storedDiffs.latestOf(EXTERNAL_ID).orElseThrow();

        assertEquals(latestId, diff.recordId());
        assertEquals(
                List.of(
                        new PullRequestDiff.File("app/a.rb", "modified", Optional.of("@@ -1 +1 @@")),
                        new PullRequestDiff.File("logo.png", "added", Optional.empty())),
                diff.files());
    }

    @Test
    void givesNothingForAPullRequestWithoutADiffRecord() {
        assertTrue(storedDiffs.latestOf("opf/openproject#8").isEmpty());
    }
}
