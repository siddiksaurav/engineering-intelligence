package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dsinnovators.ei.DatabaseTest;
import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.source.github.PullRequestDiff;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs against the settings the migrations seed, with file names of the real repository. */
class DiffFileFilterTest extends DatabaseTest {

    private static final Quarter JULY = Quarter.parse("2026-Q3");

    @Autowired
    private DiffFileFilter filter;

    private static PullRequestDiff.File file(String path) {
        return new PullRequestDiff.File(path, "modified", Optional.of("@@ patch @@"));
    }

    private static PullRequestDiff diffOf(String... paths) {
        return new PullRequestDiff(
                1, java.util.Arrays.stream(paths).map(DiffFileFilterTest::file).toList());
    }

    @Test
    void excludesLockFilesTranslationsVendoredAndGeneratedFiles() {
        List<String> excluded = List.of(
                "Gemfile.lock",
                "package-lock.json",
                "frontend/package-lock.json",
                "yarn.lock",
                "modules/bim/Gemfile.modules.lock",
                "config/locales/crowdin/de.yml",
                "modules/meeting/config/locales/crowdin/js-fr.yml",
                "vendor/gems/thing/lib/thing.rb",
                "frontend/vendor/lib.js",
                "frontend/src/app.min.js",
                "public/style.min.css",
                "public/app.js.map",
                "db/structure.sql");

        DiffFileFilter.Selection selection = filter.select(diffOf(excluded.toArray(String[]::new)), JULY);

        assertEquals(excluded, selection.excludedPaths());
        assertEquals(List.of(), selection.patchCandidates());
    }

    @Test
    void keepsFilesThatOnlyLookLikeExcludedOnes() {
        List<String> kept = List.of(
                "config/locales/en.yml",
                "modules/meeting/config/locales/en.yml",
                "app/models/vendor.rb",
                "docs/vendor/readme.md",
                "db/migrate/20260701_add_column.rb");

        DiffFileFilter.Selection selection = filter.select(diffOf(kept.toArray(String[]::new)), JULY);

        assertEquals(List.of(), selection.excludedPaths());
        assertEquals(kept.size(), selection.patchCandidates().size());
    }

    @Test
    void ordersTheCandidatesSourceThenTestThenDocsKeepingGitHubsOrderInEachGroup() {
        PullRequestDiff diff = diffOf(
                "docs/guide.md",
                "spec/models/a_spec.rb",
                "app/models/b.rb",
                "frontend/src/c.spec.ts",
                "README.md",
                "app/models/a.rb",
                "modules/meeting/spec/m_spec.rb");

        List<String> ordered = filter.select(diff, JULY).patchCandidates().stream()
                .map(PullRequestDiff.File::path)
                .toList();

        assertEquals(
                List.of(
                        "app/models/b.rb",
                        "app/models/a.rb",
                        "spec/models/a_spec.rb",
                        "frontend/src/c.spec.ts",
                        "modules/meeting/spec/m_spec.rb",
                        "docs/guide.md",
                        "README.md"),
                ordered);
    }

    @Test
    void leavesAFileWithoutAPatchOutOfTheCandidates() {
        PullRequestDiff diff = new PullRequestDiff(
                1, List.of(new PullRequestDiff.File("logo.png", "added", Optional.empty()), file("app/a.rb")));

        DiffFileFilter.Selection selection = filter.select(diff, JULY);

        assertEquals(
                List.of("app/a.rb"),
                selection.patchCandidates().stream()
                        .map(PullRequestDiff.File::path)
                        .toList());
        assertEquals(List.of(), selection.excludedPaths());
    }
}
