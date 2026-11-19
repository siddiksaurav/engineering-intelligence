package com.dsinnovators.ei.source.openproject;

import static com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn.OPENPROJECT;
import static com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn.PULL_REQUEST_BODY;
import static com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn.PULL_REQUEST_TITLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.source.openproject.PullRequestWorkItemLinks.Found;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PullRequestWorkItemLinksTest extends DatabaseTest {

    private static final String PULL_REQUEST = "opf/openproject#24204";
    private static final String OTHER_PULL_REQUEST = "opf/openproject#24205";

    @Autowired
    private PullRequestWorkItemLinks links;

    private List<Map<String, Object>> rowsOf(String pullRequest) {
        return jdbc.queryForList(
                "select work_package_id, reference, found_in, state from pull_request_work_item"
                        + " where pull_request_external_id = ? order by reference",
                pullRequest);
    }

    private static Map<String, Object> row(Long workPackageId, String reference, String foundIn, String state) {
        Map<String, Object> row = new HashMap<>();
        row.put("work_package_id", workPackageId);
        row.put("reference", reference);
        row.put("found_in", foundIn);
        row.put("state", state);
        return row;
    }

    @Test
    void aSecondWriteReplacesTheLinksOfThatPullRequestOnly() {
        links.replace(
                PULL_REQUEST,
                List.of(
                        new Found(ResolvedReference.resolved("FND-101", 81224), PULL_REQUEST_TITLE),
                        new Found(ResolvedReference.notVisible("OP-1"), PULL_REQUEST_BODY)));
        links.replace(OTHER_PULL_REQUEST, List.of(new Found(ResolvedReference.resolved("81224", 81224), OPENPROJECT)));

        links.replace(
                PULL_REQUEST, List.of(new Found(ResolvedReference.resolved("COMMS-1078", 81224), PULL_REQUEST_BODY)));

        assertEquals(List.of(row(81224L, "COMMS-1078", "pull_request_body", "resolved")), rowsOf(PULL_REQUEST));
        assertEquals(List.of(row(81224L, "81224", "openproject", "resolved")), rowsOf(OTHER_PULL_REQUEST));
    }

    @Test
    void aReferenceTheServerDoesNotShowIsStoredAsNotVisibleWithoutAWorkPackage() {
        links.replace(PULL_REQUEST, List.of(new Found(ResolvedReference.notVisible("OP-1"), PULL_REQUEST_BODY)));

        assertEquals(List.of(row(null, "OP-1", "pull_request_body", "not_visible")), rowsOf(PULL_REQUEST));
    }

    @Test
    void writingNoLinksRemovesTheEarlierOnes() {
        links.replace(PULL_REQUEST, List.of(new Found(ResolvedReference.resolved("81224", 81224), OPENPROJECT)));

        links.replace(PULL_REQUEST, List.of());

        assertEquals(List.of(), rowsOf(PULL_REQUEST));
    }
}
