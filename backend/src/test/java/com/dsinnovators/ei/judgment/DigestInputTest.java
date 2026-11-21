package com.dsinnovators.ei.judgment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dsinnovators.ei.source.github.PullRequestDiff;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DigestInputTest {

    private static final String PROMPT_VERSION = "pull-request-digest/v1";
    private static final DigestLimits ROOMY = new DigestLimits(1_000, 1_000);

    private static PullRequestDiff.File file(String path, String patch) {
        return new PullRequestDiff.File(path, "modified", Optional.ofNullable(patch));
    }

    private static DiffFileFilter.Selection sending(PullRequestDiff.File... files) {
        return new DiffFileFilter.Selection(List.of(files), List.of());
    }

    private static DigestInput inputOf(
            String body, PullRequestDiff diff, DiffFileFilter.Selection selection, DigestLimits limits) {
        return DigestInput.of(7, "Fix the filter", body, diff, selection, limits, PROMPT_VERSION);
    }

    @Test
    void namesAnExcludedFileWithoutItsPatch() {
        PullRequestDiff.File code = file("app/a.rb", "@@ code patch @@");
        PullRequestDiff.File lock = file("Gemfile.lock", "@@ lock patch @@");
        PullRequestDiff diff = new PullRequestDiff(9, List.of(code, lock));

        DigestInput input =
                inputOf("body", diff, new DiffFileFilter.Selection(List.of(code), List.of("Gemfile.lock")), ROOMY);

        assertTrue(input.text().contains("- Gemfile.lock (modified)"));
        assertTrue(input.text().contains("@@ code patch @@"));
        assertFalse(input.text().contains("@@ lock patch @@"));
        assertTrue(input.text().contains("Gemfile.lock: excluded"));
        assertFalse(input.judgedOnPartialCode());
    }

    @Test
    void listsEveryFileWithPathAndStatusOnly() {
        PullRequestDiff diff = new PullRequestDiff(
                9,
                List.of(file("app/a.rb", "@@ a @@"), new PullRequestDiff.File("logo.png", "added", Optional.empty())));

        DigestInput input = inputOf("body", diff, sending(diff.files().getFirst()), ROOMY);

        assertTrue(input.text().contains("- app/a.rb (modified)\n- logo.png (added)"));
        assertTrue(input.text().contains("logo.png: no patch from GitHub"));
        assertFalse(input.judgedOnPartialCode());
    }

    @Test
    void cutsTheBodyAtTheBudgetAndSaysSo() {
        PullRequestDiff diff = new PullRequestDiff(9, List.of());

        DigestInput input = inputOf("abcdefghij", diff, sending(), new DigestLimits(4, 1_000));

        assertTrue(input.text().contains("abcd"));
        assertFalse(input.text().contains("abcde"));
        assertTrue(input.text().contains("description cut at 4 characters"));
    }

    @Test
    void leavesTheBodyWholeWhenItFits() {
        PullRequestDiff diff = new PullRequestDiff(9, List.of());

        DigestInput input = inputOf("abcd", diff, sending(), new DigestLimits(4, 1_000));

        assertTrue(input.text().contains("abcd"));
        assertFalse(input.text().contains("description cut"));
    }

    @Test
    void addsPatchesWholeUntilTheBudgetAndNamesTheOnesLeftOut() {
        PullRequestDiff.File first = file("app/first.rb", "1234567890");
        PullRequestDiff.File tooBig = file("app/big.rb", "x".repeat(50));
        PullRequestDiff.File small = file("spec/small_spec.rb", "abc");
        PullRequestDiff diff = new PullRequestDiff(9, List.of(first, tooBig, small));

        DigestInput input = inputOf("body", diff, sending(first, tooBig, small), new DigestLimits(100, 20));

        assertTrue(input.text().contains("1234567890"));
        assertTrue(input.text().contains("abc"));
        assertFalse(input.text().contains("xxxxx"));
        assertTrue(input.text().contains("app/big.rb: over the size budget"));
        assertTrue(input.judgedOnPartialCode());
    }

    @Test
    void keepsTheOrderOfTheSelection() {
        PullRequestDiff.File source = file("app/a.rb", "SOURCE");
        PullRequestDiff.File test = file("spec/a_spec.rb", "TEST");
        PullRequestDiff diff = new PullRequestDiff(9, List.of(test, source));

        DigestInput input = inputOf("body", diff, sending(source, test), ROOMY);

        assertTrue(input.text().indexOf("SOURCE") < input.text().indexOf("TEST"));
    }

    @Test
    void sendsNoLineCounts() {
        PullRequestDiff diff = new PullRequestDiff(9, List.of(file("app/a.rb", "@@ -1 +1 @@")));

        DigestInput input = inputOf("body", diff, sending(diff.files().getFirst()), ROOMY);

        assertFalse(input.text().contains("additions"));
        assertFalse(input.text().contains("deletions"));
    }

    @Test
    void hashesTheKindThePromptVersionTheTextAndTheSourceRecords() {
        PullRequestDiff diff = new PullRequestDiff(9, List.of(file("app/a.rb", "@@ a @@")));
        DiffFileFilter.Selection selection = sending(diff.files().getFirst());
        DigestInput base = inputOf("body", diff, selection, ROOMY);

        assertEquals(base.hash(), inputOf("body", diff, selection, ROOMY).hash());
        assertEquals(64, base.hash().length());
        assertNotEquals(
                base.hash(),
                DigestInput.of(7, "Fix the filter", "body", diff, selection, ROOMY, "pull-request-digest/v2")
                        .hash());
        assertNotEquals(
                base.hash(),
                DigestInput.of(8, "Fix the filter", "body", diff, selection, ROOMY, PROMPT_VERSION)
                        .hash());
        assertNotEquals(
                base.hash(),
                inputOf("body", new PullRequestDiff(10, diff.files()), selection, ROOMY)
                        .hash());
        assertNotEquals(
                base.hash(), inputOf("other body", diff, selection, ROOMY).hash());
    }
}
