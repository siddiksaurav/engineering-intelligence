package com.dsinnovators.ei.source.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GraphQlDocumentsTest {

    private static int count(String text, String part) {
        Matcher matcher = Pattern.compile(Pattern.quote(part)).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void aQueryGetsEveryFragmentItUsesOnceEvenThroughOtherFragments() {
        String query = GraphQlDocuments.read("pull-request");

        assertEquals(1, count(query, "fragment Actor on Actor"));
        assertEquals(1, count(query, "fragment PullRequestFields on PullRequest"));
        assertEquals(1, count(query, "fragment ReviewThread on PullRequestReviewThread"));
        assertEquals(1, count(query, "fragment ReviewComment on PullRequestReviewComment"));
        assertEquals(1, count(query, "fragment PullRequestComment on IssueComment"));
    }

    @Test
    void aFragmentThatIsNotUsedIsNotAdded() {
        String query = GraphQlDocuments.read("pull-request-files");

        assertEquals(1, count(query, "fragment ChangedFile on PullRequestChangedFile"));
        assertFalse(query.contains("fragment Actor"));
    }

    @Test
    void aQueryWithoutFragmentsIsReadAsItIs() {
        assertFalse(GraphQlDocuments.read("search-merged").contains("fragment"));
    }

    @Test
    void anUnknownQueryIsNamed() {
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> GraphQlDocuments.read("nope"));

        assertEquals("No GraphQL query on the classpath at github/nope.graphql", missing.getMessage());
    }
}
