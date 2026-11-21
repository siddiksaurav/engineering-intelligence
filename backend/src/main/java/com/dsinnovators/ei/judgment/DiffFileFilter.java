package com.dsinnovators.ei.judgment;

import com.dsinnovators.ei.common.Quarter;
import com.dsinnovators.ei.setting.SettingService;
import com.dsinnovators.ei.source.github.PullRequestDiff;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Decides which patches of a pull request may be sent to the model and in what order (decision 133).
 * Lock files, machine translations, vendored and generated files are named in the file list but their
 * patch is not sent; the paths are the setting {@code judgment.diff_excluded_paths} (decision 131).
 */
@Component
class DiffFileFilter {

    private static final String EXCLUDED_PATHS_SETTING = "judgment.diff_excluded_paths";
    private static final String TEST_PATHS_SETTING = "evidence.test_paths";

    /** Only orders the patches when the budget is short; it decides no evidence. */
    private static final PathPatterns DOCUMENTATION_PATHS =
            new PathPatterns(List.of("docs/", "*.md", "*.adoc", "*.rdoc", "*.txt"));

    private enum Group {
        SOURCE,
        TEST,
        DOCUMENTATION
    }

    /**
     * What may be sent.
     *
     * @param patchCandidates files with a patch that is not excluded, source files first, then test
     *     files, then documentation, each group in GitHub's order
     * @param excludedPaths the files whose patch is withheld by the setting, in GitHub's order
     */
    record Selection(List<PullRequestDiff.File> patchCandidates, List<String> excludedPaths) {

        Selection {
            patchCandidates = List.copyOf(patchCandidates);
            excludedPaths = List.copyOf(excludedPaths);
        }
    }

    private final SettingService settings;

    DiffFileFilter(SettingService settings) {
        this.settings = settings;
    }

    /** Sorts the files of the diff with the settings in force in {@code quarter}. */
    Selection select(PullRequestDiff diff, Quarter quarter) {
        PathPatterns excluded = new PathPatterns(settings.getTextList(EXCLUDED_PATHS_SETTING, quarter));
        PathPatterns tests = new PathPatterns(settings.getTextList(TEST_PATHS_SETTING, quarter));
        List<String> excludedPaths = diff.files().stream()
                .map(PullRequestDiff.File::path)
                .filter(excluded::matches)
                .toList();
        List<PullRequestDiff.File> candidates = diff.files().stream()
                .filter(file -> file.patch().isPresent() && !excluded.matches(file.path()))
                // A stable sort: the files of one group stay in GitHub's order.
                .sorted(Comparator.comparing(file -> groupOf(file.path(), tests)))
                .toList();
        return new Selection(candidates, excludedPaths);
    }

    private static Group groupOf(String path, PathPatterns tests) {
        if (tests.matches(path)) {
            return Group.TEST;
        }
        return DOCUMENTATION_PATHS.matches(path) ? Group.DOCUMENTATION : Group.SOURCE;
    }
}
