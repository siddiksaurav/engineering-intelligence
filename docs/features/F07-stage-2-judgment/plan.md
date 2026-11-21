# F07 Stage 2 Feature work judgment and review comment judgment: plan

Spec: `docs/features/F07-stage-2-judgment/design.md` (approved on 2026-11-22 by the session on the user's instruction). Decisions 163 to 176.

Every task: test first, one commit, only the tests it touches (decision 73), plus the architecture tests when it adds a package, table or entity. No test calls a real model (decision 142). All paths below `backend/src/main/java/com/dsinnovators/ei/` or the matching test folder unless given in full.

## Batch 1: tasks 1 to 4 (kinds, settings, prompts, answers, limits)

### Task 1. Migration V11 and the two judgment kinds
`judgment.kind` check gains `feature_work_complexity` and `review_comment`; settings `evidence.complexity_tiers` and `evidence.review_comment_types` (valid from 2026-Q2, values of design "Migration V11"). `JudgmentKind` gets `FEATURE_WORK_COMPLEXITY` and `REVIEW_COMMENT`, both with the evidence item as subject.
- Files: `backend/src/main/resources/db/migration/V11__stage_2_judgment.sql`, `judgment/JudgmentKind.java`; tests `judgment/JudgmentTableTest.java`, `judgment/JudgmentKindTest.java`, `SchemaTest.java` if it lists migrations.
- **Test first:** `JudgmentTableTest.acceptsTheStage2KindsOnAnEvidenceItem`: a row of each new kind on an item row is stored.
- Commit: `feat(F07): stage 2 judgment kinds and tier settings`

### Task 2. Stage 2 limits as properties
`ei.judgment.max-description-characters` 1,500, `max-feature-work-characters` 14,000, `max-hunk-characters` 2,000, `max-comment-characters` 2,000, `max-replied-comment-characters` 500, `max-review-characters` 12,000, each checked more than zero; the start-up check that one comment's parts plus the PR part fit `max-review-characters`. Grouped into records the builders take (as `DigestLimits`).
- Files: `judgment/JudgmentProperties.java`, new `judgment/FeatureWorkLimits.java`, `judgment/ReviewCommentLimits.java`, `backend/src/main/resources/application.yml`; test `judgment/JudgmentPropertiesTest.java`.
- **Test first:** `JudgmentPropertiesTest.refusesReviewLimitsWhereOneCommentCannotFitACall`.
- Commit: `feat(F07): stage 2 input limits`

### Task 3. Feature work prompt, schema and answer
Prompt `judgment/prompts/feature-work-complexity-v1.md` (complexity table with the softened module rule and the five-module rename example, the two limits, the 13 work types, L1 to L4, quote only the input, cite source keys) and its strict schema `judgment/schemas/feature-work-complexity-v1.json`. `FeatureWorkAnswer`: reads and checks an answer (enums, at least one fact with fact text, confidence 0 to 1, every field present) and refuses it with `InvalidAnswerException` (generalise F06's `InvalidDigestException` if it fits, removing the old one). Hashes pinned in `PromptLibraryTest`.
- Files: the two resources, `judgment/FeatureWorkAnswer.java`, the exception; tests `FeatureWorkAnswerTest.java`, `PromptLibraryTest.java`.
- **Test first:** `FeatureWorkAnswerTest.refusesAConfidenceAboveOne`.
- Commit: `feat(F07): feature work prompt and answer`

### Task 4. Review comment prompt, schema and answer
Prompt `judgment/prompts/review-comments-v1.md` (the ten types with their meaning, spec 6.4 row and 7.2 mix as context, judge each comment on its own, cite each comment id once) and schema `judgment/schemas/review-comments-v1.json`. `ReviewCommentAnswer`: reads and checks an answer against the ids sent (every id exactly once, no unknown id, a known type, a reason). `ReviewCommentTypes`: reads `evidence.review_comment_types` (substantive, tier or none). Hashes pinned; a test that the schema's type enum equals the setting's names.
- Files: the two resources, `judgment/ReviewCommentAnswer.java`, `judgment/ReviewCommentTypes.java`; tests `ReviewCommentAnswerTest.java`, `ReviewCommentTypesTest.java`, `PromptLibraryTest.java`.
- **Test first:** `ReviewCommentAnswerTest.refusesAnAnswerThatLeavesOutAComment`.
- Commit: `feat(F07): review comment prompt and answer`

## Batch 2: tasks 5 to 8 (Feature work)

### Task 5. The Feature work items handed to judgment
`judgment.FeatureWorkItems` (interface) and the record `FeatureWorkToJudge` (item row id, key, source URL, source record ids, feature number or none, task numbers, PR external ids). `evidence.FeatureWorkItemsForJudgment` implements it from the current `feature_work` rows and their `fields` and sources.
- Files: `judgment/FeatureWorkItems.java`, `judgment/FeatureWorkToJudge.java`, `evidence/FeatureWorkItemsForJudgment.java`; test `evidence/FeatureWorkItemsForJudgmentTest.java`.
- **Test first:** `FeatureWorkItemsForJudgmentTest.handsOverEveryCurrentFeatureWorkItemWithItsTasksAndPullRequests` (a withdrawn and a superseded row are not handed over).
- Commit: `feat(F07): feature work items for judgment`

### Task 6. Feature work input
Reader of work item texts in `source.openproject` (title, type, priority, parent title, description, URL). `FeatureWorkInput`: the tagged parts in design order, digests in short form, the cuts and budget of decision 168, `judgedOnPartialInput` and `leftOutPullRequests`, the source links by key, and the input hash (kind, prompt version, text, item row id, source record ids, digest judgment ids).
- Files: `source/openproject/WorkPackageText.java` and a method on `StoredWorkPackages`, `judgment/FeatureWorkInput.java`; tests `FeatureWorkInputTest.java`, `source/openproject/StoredWorkPackagesTest.java` (or the existing reader test).
- **Test first:** `FeatureWorkInputTest.leavesOutTheNewestPullRequestsByNameWhenTheDigestsDoNotFit`.
- Commit: `feat(F07): feature work input`

### Task 7. Feature work judge
`FeatureWorkJudge.judge(FeatureWorkToJudge, quarter, counts)`: waits for missing digests, skips a known hash, asks the model (once more on a refused or unreadable answer; share the ask-again logic with `PullRequestDigester`, no copy), tier from `evidence.complexity_tiers`, facts with system links, confidence, writes the judgment.
- Files: `judgment/FeatureWorkJudge.java`, shared ask-again helper, `judgment/PullRequestDigester.java`; test `FeatureWorkJudgeTest.java`.
- **Test first:** `FeatureWorkJudgeTest.writesTheTierOfTheComplexityFromTheSetting` (very_high gives T3).
- Commit: `feat(F07): feature work judge`

### Task 8. Job `feature-work-judgment` and the model job guard
The job of design "Main flows" 1 with its counts; `ModelJobGuard` so `pull-request-digest`, `feature-work-judgment` and `review-comment-judgment` never run at once (decision 174), used by all three.
- Files: `judgment/FeatureWorkJudgmentJob.java`, `judgment/ModelJobGuard.java`, `judgment/PullRequestDigestJob.java`; tests `FeatureWorkJudgmentJobTest.java`, `ModelJobGuardTest.java`.
- **Test first:** `FeatureWorkJudgmentJobTest.aSecondRunWithTheSameInputMakesNoCall`.
- Commit: `feat(F07): feature-work-judgment job`

## Batch 3: tasks 9 to 12 (review comments)

### Task 9. The review comment items handed to judgment
`judgment.ReviewCommentItems` and `ReviewCommentToJudge` (item row id, key, comment record id, PR record id, PR external id); `evidence.ReviewCommentItemsForJudgment` implements it from the current `review_comment_written` rows.
- Files: `judgment/ReviewCommentItems.java`, `judgment/ReviewCommentToJudge.java`, `evidence/ReviewCommentItemsForJudgment.java`; test `evidence/ReviewCommentItemsForJudgmentTest.java`.
- **Test first:** `ReviewCommentItemsForJudgmentTest.handsOverEveryCurrentReviewCommentItem`.
- Commit: `feat(F07): review comment items for judgment`

### Task 10. Review comment input and chunks
Reader of the comment text in `source.github` (body, diff hunk, the comment it answers). `ReviewCommentChunks`: per PR, comments in creation order, the PR part, each comment's tagged part with the hunk tail and the cuts, split at `max-review-characters`; per comment its hash.
- Files: `source/github/ReviewCommentText.java` and a reader method, `judgment/ReviewCommentChunks.java`; tests `ReviewCommentChunksTest.java`, the source reader test.
- **Test first:** `ReviewCommentChunksTest.sendsTheTailOfALongDiffHunk`.
- Commit: `feat(F07): review comment input`

### Task 11. Review comment judge
`ReviewCommentJudge.judge(chunk, quarter, counts)`: skips a chunk whose every comment hash is stored, asks (once more on a refusal), reads substantive and tier from `ReviewCommentTypes`, writes the chunk's judgments in one transaction (`JudgmentStore.writeAll`).
- Files: `judgment/ReviewCommentJudge.java`, `judgment/JudgmentStore.java`; tests `ReviewCommentJudgeTest.java`, `JudgmentStoreTest.java`.
- **Test first:** `ReviewCommentJudgeTest.aStyleCommentGetsNoTier`.
- Commit: `feat(F07): review comment judge`

### Task 12. Job `review-comment-judgment`
The job of design "Main flows" 2 with its counts, the guard of task 8, limit reached and failures.
- Files: `judgment/ReviewCommentJudgmentJob.java`; test `ReviewCommentJudgmentJobTest.java`.
- **Test first:** `ReviewCommentJudgmentJobTest.everyCurrentReviewCommentGetsOneJudgment`.
- Commit: `feat(F07): review-comment-judgment job`
