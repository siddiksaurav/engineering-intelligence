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

## Follow-ups

- `ReviewCommentLimits`: the start-up check adds the four cut limits but not the text that always comes with them (PR title, file path, labels, cut marks). With the defaults (6,000 of 12,000) there is room; an oversized comment would go in a chunk of its own.
- `items_left_for_later` (both Stage 2 jobs) counts every remaining item, including those the next run will skip as already judged. Counts only; no judgment is affected.

## Verification (22 Nov 2026)

No LLM key on this machine (user, 22 Nov 2026), so every real-model check waits for the user.

- Backend: full `./mvnw verify` green after the review fixes: 896 tests, 0 failures, 2 skipped (F10, F11). The backend could not be started against the Compose database: no `.env` (no `POSTGRES_PASSWORD`) on this machine, so V11 is not yet applied there; it runs at the next start. Web: format, lint, typecheck, 50 tests and build green (no web change in F07).
- "Done when" 1 (every closed Feature work item has one current complexity judgment, every fact with a quote or link): `FeatureWorkJudgmentJobTest` and `FeatureWorkJudgeTest` green with the fake model. Real data waiting for the user's key.
- "Done when" 2 (every human reviewer comment has one current judgment): `ReviewCommentJudgmentJobTest.everyCurrentReviewCommentGetsOneJudgment` green. Real data waiting for the key.
- "Done when" 3 (a rename across five modules is judged Low): the prompt states the rule and the example (`PromptLibraryTest`). The model's behaviour waits for the key.

### Steps for the user once the key is in `.env`

1. `cp .env.example .env`, set `POSTGRES_PASSWORD`, `LLM_PROVIDER_1_API_KEY` (Groq) and optionally `LLM_PROVIDER_2_API_KEY` and `LLM_PROVIDER_2_MODEL` (Gemini). Start the backend.
2. Run, one after the other (each `POST ... -d '{}'`, check with `GET /api/jobs/runs/<id>`): `pull-request-digest` (may need several days of the free limit; run again until `pull_requests_left_for_later` is 0), `evidence-build`, `feature-work-judgment`, `review-comment-judgment`.
3. SQL checks (Compose database):
   - Current Feature work items without a current `feature_work_complexity` judgment: 0.
   - Current Review comment written items without a current `review_comment` judgment: 0.
   - `select count(*) from judgment j, jsonb_array_elements(j.facts) f where j.kind in ('feature_work_complexity','review_comment') and coalesce(f->>'link','') = '';` gives 0.
4. Five-module rename: find a Feature work item whose PR digests name five or more modules and whose nature of change is a rename; its complexity should be `low`. If none is in the two weeks, note it for F08's tuning set.

## What a later feature should know

- Job order: `github-import`, `openproject-import`, `identity-build`, `pull-request-digest`, `evidence-build`, `feature-work-judgment`, `review-comment-judgment`. The three model jobs never run at once (decision 174); a second start fails naming the running one.
- `feature_work_complexity` value: `workType`, `complexity` (`low` to `very_high`), `rubric`, `facts` (with `quote`, `source`), `reason`, `judgedOnPartialInput`, `leftOutPullRequests`; tier on the row (Very high gives T3, decision 171); confidence in the `confidence` column only.
- `review_comment` value: `commentType`, `substantive`, `reason`; tier null when not substantive (decision 170). F10 reads substantive from the value, not from the setting.
- No screen or API shows these judgments yet (decision 175): F08 adds them after the blind labels.
- Prompt tuning (F08) adds `feature-work-complexity/v2` or `review-comments/v2` and pins its hash in `PromptLibraryTest`.
