# F07 Stage 2 Feature work judgment and review comment judgment: notes

## Session note

The user asked the session to decide every open point and to skip the real model runs (no LLM key, 22 Nov 2026). No question brief was answered; decisions 163 to 176 are the session's and may be overruled by number.

## Differences from the plan

- Batch 1: `InvalidDigestException` became `InvalidAnswerException`, shared by the three answers; field readers moved from `PullRequestDigest` to a new `AnswerFields`. `FeatureWorkAnswer.toValue` is added by task 7, which knows `judgedOnPartialInput` and `leftOutPullRequests`. A fact may have an empty `quote` or `source` (its text is required); an empty or unknown source gets the item's source link. Comment ids in the review input and answer are `C-<databaseId>`. `ReviewCommentTypes.classify` fails naming the type when the setting misses it or gives a substantive type no tier. Java 25 on this machine is `/usr/lib/jvm/jdk-25-oracle-x64`.
- Batch 2: `judgment.CheckedAnswers` is the one ask-again helper (once more on an invalid or unreadable answer), used by `PullRequestDigester` and `FeatureWorkJudge`; the review judge uses it too. `ProviderChain.startRunRequiringAProvider` and `reportUseTo` replace the same lines of `PullRequestDigestJob`. `TextCuts` (cut with its line) and `PullRequestBody` are shared by the digest and Feature work inputs. `JudgmentJobFailedException` is the failure of the two Stage 2 jobs.
- The model's confidence is stored in the column `judgment.confidence` (`NewJudgment.confidence`, a nullable `Double` checked 0 to 1; null for digests and item tiers), and only there: `value` does not repeat it.
- `leftOutPullRequests` holds external ids (`opf/openproject#24204`); the input text names them `PR-24204`. The oldest PR always stays in the input. Cuts for budget: task descriptions are halved first, then the feature description, only while a halving still shortens the text; no number constant in `judgment` (SpecNumbersTest).
- `FeatureWorkToJudge.taskNumbers` leaves out the Feature itself (a PR that links the Feature directly lists it among the tasks of the item).
- `ModelJobGuard` is in memory: it guards one backend process, which is the only way jobs run. `review-comment-judgment` (batch 3) takes it the same way: `enter(name())` and `close()` in a `finally`.
- Batch 3: `source.github.StoredReviewComments` gives a comment as `ReviewCommentText`: `textOfRecord` reads the exact record version an item was built from, `latestTextOf` the latest version of the comment a reply answers (by its database id, whoever wrote it). `ReviewCommentChunks` takes the pull request part and the comment parts the job assembles from the items and the texts. `TextCuts` got a subject for the cut line (`comment cut at ...`) and `tail` for the hunk (`[diff hunk cut: only the last N characters are shown]` before the tail).
  The review judge writes only the comments of a chunk whose hash is not stored yet, and counts the others in `skipped_same_input` (a new item row of one comment changes only that comment's hash, so a chunk can be half judged); it makes no call when none is left. `JudgmentStore.writeAll` is one transaction over `write`. The fact quote length is the property `ei.judgment.max-quote-characters` (200); it counts code points.
  The job builds all chunks first, then judges them in order; `chunks` counts them all, and a stop at the provider limit counts the comments of the chunk it stopped at and of every later one in `items_left_for_later` (a count of comments, as the plan's key says). A pull request whose comments cannot be read (not stored, or a comment record that cannot be read) counts as one failed chunk in `chunks_failed`, named by the pull request. `StoredPullRequests.findByExternalId` replaces the id parsing that `FeatureWorkJudge` did itself.

## Review (one round, 22 Nov 2026)

Nothing blocking. Fixed as follow-ups:

- `181a760`: the quote length is the property `ei.judgment.max-quote-characters`, read by `ReviewCommentJudge` through `JudgmentProperties` (not through `ReviewCommentLimits`, which is the call budget); the review limits are built only in `reviewCommentLimits()`, so an impossible budget now fails at the start of the review job instead of in the properties constructor. Tests build properties with `JudgmentPropertiesBuilder`.
- `b14df75`: the review job reads each item's comment by the exact record version (`RawRecordStore.findById`), the answered comment by its latest version, and no longer reads all comments up front; a record that cannot be read fails only its pull request, and `ReviewCommentText` names it by id and external id. An edited comment is judged on the version its item was built from until `evidence-build` gives it a new item row.
- `d2ebf59`: tests for a new comment superseding the old judgments of its pull request and for a reply whose parent is not stored.
- `c460f9c`: `ModelJobGuard.runGuarded`, `JobArguments.requireNone` and `JudgingLoop` replace the copies in the three model jobs.
- `6b50a78`: confidence only in the column.
- `fc0b8b3`: title through `PullRequestFacts`, `WireNamed` at package level, imports in two tests.
