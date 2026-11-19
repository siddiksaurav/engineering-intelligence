package com.dsinnovators.ei.source.openproject;

import static com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn.PULL_REQUEST_BODY;
import static com.dsinnovators.ei.source.openproject.WorkPackageReference.FoundIn.PULL_REQUEST_TITLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkPackageReferenceFinderTest {

    private final WorkPackageReferenceFinder finder =
            new WorkPackageReferenceFinder(URI.create("https://community.openproject.org"));

    @Test
    void findsKeysAndNumbersInEveryUrlFormAndABracketedKeyInTheTitle() {
        String body = """
                https://community.openproject.org/wp/AGILE-218
                https://community.openproject.org/work_packages/op-19204
                https://community.openproject.org/projects/SJF/work_packages/COMMS-1078/activity
                https://community.openproject.org/projects/openproject/work_packages/56343
                https://community.openproject.org/wp/71971
                """;

        List<WorkPackageReference> references = finder.find("[OP-19345] Fix the thing", body);

        assertThat(references)
                .containsExactly(
                        new WorkPackageReference("AGILE-218", PULL_REQUEST_BODY),
                        new WorkPackageReference("OP-19204", PULL_REQUEST_BODY),
                        new WorkPackageReference("COMMS-1078", PULL_REQUEST_BODY),
                        new WorkPackageReference("56343", PULL_REQUEST_BODY),
                        new WorkPackageReference("71971", PULL_REQUEST_BODY),
                        new WorkPackageReference("OP-19345", PULL_REQUEST_TITLE));
    }

    @Test
    void givesNothingForABranchNameOrAnUnrelatedLink() {
        String body = "Branch housekeeping/npm-11-allow-scripts, see https://example.org/wp/OP-1";

        assertThat(finder.find("Bump npm", body)).isEmpty();
    }

    @Test
    void listsAReferenceOnceWherePerPart() {
        String body = "https://community.openproject.org/wp/OP-1 and https://community.openproject.org/wp/op-1";

        assertThat(finder.find("[OP-1] same", body))
                .containsExactly(
                        new WorkPackageReference("OP-1", PULL_REQUEST_BODY),
                        new WorkPackageReference("OP-1", PULL_REQUEST_TITLE));
    }

    @Test
    void refusesABaseUrlWithoutHost() {
        assertThatThrownBy(() -> new WorkPackageReferenceFinder(URI.create("/relative")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/relative");
    }
}
