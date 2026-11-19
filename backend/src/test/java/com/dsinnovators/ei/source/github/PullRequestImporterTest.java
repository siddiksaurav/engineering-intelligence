package com.dsinnovators.ei.source.github;

import static com.dsinnovators.ei.source.github.GitHubResponses.answer;
import static com.dsinnovators.ei.source.github.GitHubResponses.diffFiles;
import static com.dsinnovators.ei.source.github.GitHubResponses.pullRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.job.JobCounts;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PullRequestImporterTest extends DatabaseTest {

    @Autowired
    private PullRequestRecorder recorder;

    private final GitHubApiFixture github = new GitHubApiFixture();
    private final Map<String, Long> counts = new TreeMap<>();
    private final JobCounts jobCounts = (name, amount) -> counts.merge(name, amount, Long::sum);

    private PullRequestImporter importer() {
        return new PullRequestImporter(new PullRequestFetcher(github.api), recorder);
    }

    private void expectPullRequest(int number) {
        github.server
                .expect(requestTo(GitHubApiFixture.GRAPHQL))
                .andRespond(withSuccess(answer(pullRequest(number)), MediaType.APPLICATION_JSON));
        github.server
                .expect(requestTo(
                        "https://api.github.com/repos/opf/openproject/pulls/" + number + "/files?per_page=100"))
                .andRespond(withSuccess(diffFiles(), MediaType.APPLICATION_JSON));
    }

    @Test
    void importsAMergedPullRequestAndCountsEachRecordByKindAndOutcome() throws Exception {
        expectPullRequest(7);

        importer().importPullRequest("opf/openproject", 7, jobCounts);

        assertEquals(1L, counts.get("pull_request.created"));
        assertEquals(1L, counts.get("pull_request_diff.created"));
    }

    @Test
    void importingTheSamePullRequestAgainCountsItUnchanged() throws Exception {
        expectPullRequest(7);
        expectPullRequest(7);
        importer().importPullRequest("opf/openproject", 7, jobCounts);
        counts.clear();

        importer().importPullRequest("opf/openproject", 7, jobCounts);

        assertEquals(1L, counts.get("pull_request.unchanged"));
    }
}
