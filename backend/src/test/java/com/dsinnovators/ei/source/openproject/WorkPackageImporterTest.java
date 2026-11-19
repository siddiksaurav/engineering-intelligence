package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.job.JobCounts;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class WorkPackageImporterTest {

    private static final JsonNode WORK_PACKAGE =
            CanonicalJson.parse("{\"id\":20,\"_links\":{\"project\":{\"href\":\"/api/v3/projects/1395\"}}}");

    private final WorkPackageFetcher fetcher = mock(WorkPackageFetcher.class);
    private final WorkPackageRecorder workPackageRecorder = mock(WorkPackageRecorder.class);
    private final ProjectRecorder projectRecorder = mock(ProjectRecorder.class);
    private final JobCounts counts = mock(JobCounts.class);
    private final WorkPackageImporter importer = new WorkPackageImporter(fetcher, workPackageRecorder, projectRecorder);

    private FetchedWorkPackage fetchedWorkPackage() throws InterruptedException {
        FetchedWorkPackage fetched = new FetchedWorkPackage(
                20,
                WORK_PACKAGE,
                CanonicalJson.parse("{\"_embedded\":{\"elements\":[]}}"),
                CanonicalJson.parse("{\"reduced\":true,\"pullRequests\":[]}"));
        when(fetcher.fetchWithDetails(WORK_PACKAGE)).thenReturn(fetched);
        when(workPackageRecorder.store(fetched)).thenReturn(List.of());
        return fetched;
    }

    @Test
    void aProjectCallThatFailsFailsTheImportAtOnce() throws Exception {
        fetchedWorkPackage();
        OpenProjectApiException failure = new OpenProjectApiException("project 1395 could not be read");
        when(fetcher.fetchProject(1395)).thenThrow(failure);

        OpenProjectApiException thrown =
                assertThrows(OpenProjectApiException.class, () -> importer.importAll(List.of(WORK_PACKAGE), counts));

        assertSame(failure, thrown);
        verify(projectRecorder, never()).store(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aWorkPackageThatFailsIsCountedAndTheOthersGoOn() throws Exception {
        JsonNode broken = CanonicalJson.parse("{\"id\":21,\"_links\":{}}");
        when(fetcher.fetchWithDetails(broken)).thenThrow(new OpenProjectApiException("no activities"));
        FetchedWorkPackage fetched = fetchedWorkPackage();
        when(fetcher.fetchProject(1395)).thenReturn(CanonicalJson.parse("{\"id\":1395}"));
        when(projectRecorder.store(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new StoredRecord(
                        OpenProjectRecordKind.PROJECT, com.dsinnovators.ei.source.StoreResult.Outcome.CREATED));

        WorkPackageImporter.Imported imported = importer.importAll(List.of(broken, WORK_PACKAGE), counts);

        assertEquals(List.of(fetched), imported.stored());
        assertEquals(List.of(21L), imported.failedNumbers());
        verify(counts).add("work_packages_failed", 1);
    }
}
