package com.dsinnovators.ei.source.openproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.CanonicalJson;
import com.dsinnovators.ei.source.RawRecordStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class WorkPackageRecorderRollbackTest extends DatabaseTest {

    @Autowired
    private WorkPackageRecorder recorder;

    @MockitoSpyBean
    private RawRecordStore store;

    @Test
    void aStoreThatFailsHalfwayLeavesNoRowOfTheWorkPackage() {
        doThrow(new IllegalStateException("the database refused the pull request list"))
                .when(store)
                .store(eq("openproject"), eq("work_package_pull_requests"), eq("81224"), any(), any());
        FetchedWorkPackage fetched = new FetchedWorkPackage(
                81224,
                CanonicalJson.parse("{\"id\":81224}"),
                CanonicalJson.parse("{\"_embedded\":{\"elements\":[{\"id\":1756926}]}}"),
                CanonicalJson.parse("{\"reduced\":true,\"pullRequests\":[]}"));

        assertThrows(IllegalStateException.class, () -> recorder.store(fetched));

        assertEquals(0, jdbc.queryForObject("select count(*) from raw_record", Integer.class));
    }
}
