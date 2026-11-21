# F06 JudgmentModel, Stage 1 PR digest, and the builders that need the PR work type: plan

Spec: `docs/features/F06-pr-digest/design.md` (approved on 2026-11-22). Decisions 127 to 158.

Every task: test first, one commit, only the tests it touches (decision 73), plus the architecture tests when it adds a package, table or entity. No test calls a real model (decision 142).

## Batch 1: tasks 1 to 4 (judgment table, store, model client)

### Task 1. Migration V9
Table `judgment` with its checks, the append-only trigger and the unique `(kind, input_hash)` index for model judgments; the five new evidence types in the `evidence_item.evidence_type` check; source role `pull_request_diff` in the `evidence_item_source.role` check; setting rows `judgment.diff_excluded_paths`, `evidence.test_paths`, `evidence.test_kind_tiers` (valid from 2026-Q2). `judgment` added to the `DatabaseTest` truncate line. Enables `AppendOnlyTablesTest.judgmentHasATriggerBeforeEveryUpdateAndDelete`.
- Files: `backend/src/main/resources/db/migration/V9__judgment.sql`, `DatabaseTest.java`, `SchemaTest.java`, `AppendOnlyTablesTest.java`, new `judgment/JudgmentTableTest.java`.
- **Test first:** `JudgmentTableTest.refusesARowWithBothSubjects`: an insert naming a PR record and an evidence item is refused by the check.
- Commit: `feat(F06): judgment table, new evidence types and settings`

### Task 2. Judgment entity and store
`Judgment` (immutable entity), `JudgmentRepository` (no update or delete), `JudgmentStore`: write a judgment (refuses a blank reason, no fact, a fact without a link, a model judgment without model or prompt version, an existing model input hash), supersede the current judgment of the same subject and kind, find the current judgment of a subject and kind. Enables `AppendOnlyCodeTest` x2 and `SpecNumbersTest.theJudgmentPackageHoldsNoNumberConstant`.
- Files: `judgment/Judgment.java`, `JudgmentKind.java`, `JudgmentRepository.java`, `JudgmentStore.java`, `NewJudgment.java`, `JudgmentFact.java`; tests `JudgmentStoreTest.java`, `AppendOnlyCodeTest.java`, `SpecNumbersTest.java`.
- **Test first:** `JudgmentStoreTest.refusesAFactWithoutALink`.
- Commit: `feat(F06): judgment store`

### Task 3. JudgmentModel and the OpenAI-compatible client
`JudgmentModel`, `ModelRequest`, `ModelAnswer`; `OpenAiCompatibleClient` for one provider on `RestClient`: request body (model, system and user messages, strict JSON schema, reasoning effort when set), answer and token usage parsed, a short 429 waited out from `retry-after` or `x-ratelimit-reset-*`, a long one reported as the provider's limit, 413, 400 and 5xx after retries as exceptions with the status and never the key. A mock-server fixture for tests. Enables both `JudgmentModelBoundaryTest` tests.
- Files: `judgment/JudgmentModel.java`, `ModelRequest.java`, `ModelAnswer.java`, `OpenAiCompatibleClient.java`, `ModelCallException.java`, `ProviderLimitReachedException.java`; tests `OpenAiCompatibleClientTest.java`, `ModelApiFixture.java`, `JudgmentModelBoundaryTest.java`.
- **Test first:** `OpenAiCompatibleClientTest.sendsTheStrictSchemaTheModelAndTheReasoningEffort`.
- Commit: `feat(F06): OpenAI-compatible model client`

### Task 4. Provider chain and configuration
`JudgmentProperties` (`ei.judgment.*`: providers list with name, base URL, key, model, reasoning effort; timeout, retries, retry wait, max rate-limit wait, max diff and body characters), validated at start; `ProviderChain implements JudgmentModel` (first provider not at its limit; `ModelLimitReachedException` when none is left; counts calls per provider); `JudgmentConfiguration` bean; `application.yml` and `.env.example` with the `LLM_PROVIDER_1_*` and `LLM_PROVIDER_2_*` names (decision 158). A provider without a key is left out.
- Files: `judgment/JudgmentProperties.java`, `ProviderChain.java`, `ModelLimitReachedException.java`, `JudgmentConfiguration.java`, `application.yml`, `.env.example`; tests `ProviderChainTest.java`, `JudgmentPropertiesTest.java`.
- **Test first:** `ProviderChainTest.movesToTheNextProviderWhenOneReachesItsLimit`.
- Commit: `feat(F06): provider chain over free model providers`

## Batch 2: tasks 5 to 8 (digest input, prompt, job)

### Task 5. Stored diffs reader
`StoredPullRequestDiffs` in `source.github`: the latest `pull_request_diff` record of a PR, as `PullRequestDiff` (record id, files with path, status, patch or none).
- Files: `source/github/StoredPullRequestDiffs.java`, `PullRequestDiff.java`; test `StoredPullRequestDiffsTest.java`.
- **Test first:** `StoredPullRequestDiffsTest.readsTheFilesOfTheLatestDiffVersion`.
- Commit: `feat(F06): reader of stored pull request diffs`

### Task 6. Digest input
`DigestInput`: title, body cut at the body budget, file list with path and status only (no line counts), patches filtered by `judgment.diff_excluded_paths`, added whole in the order source, test, docs until the diff budget, left-out files named, `judgedOnPartialCode`; the input text and its hash over kind, prompt version, input text and source record ids (decision 157).
- Files: `judgment/DigestInput.java`, `DiffFileFilter.java`, `PathPatterns.java`; tests `DigestInputTest.java`, `DiffFileFilterTest.java`.
- **Test first:** `DigestInputTest.namesAnExcludedFileWithoutItsPatch`.
- Commit: `feat(F06): digest input with the diff file filter and budget`

### Task 7. Prompt, schema and digest answer
The prompt `judgment/prompts/pull-request-digest-v1.md` and its JSON schema; `PromptLibrary` (loads a prompt by version); `PullRequestDigest` record read from an answer and checked (13 work types, the enums, required fields); facts given links by the system (the PR's files page for a named changed file, else the PR). A test pins the SHA-256 of each released prompt file.
- Files: `judgment/PromptLibrary.java`, `PullRequestDigest.java`, `DigestFacts.java`, `resources/judgment/prompts/pull-request-digest-v1.md`, `resources/judgment/schemas/pull-request-digest-v1.json`; tests `PromptLibraryTest.java`, `PullRequestDigestTest.java`.
- **Test first:** `PullRequestDigestTest.refusesAWorkTypeOutsideTheThirteen`.
- Commit: `feat(F06): pull request digest prompt and answer`

### Task 8. Job `pull-request-digest`
Arguments none or `pullRequestNumbers`; bot PRs and PRs without author skipped and counted; one PR per transaction; same input hash skipped without a call; the digest written superseding the current one; one retry on a bad answer; failures counted and named at the end; every provider at its limit stops the run, counts what is left and succeeds; counts of calls, tokens, waits, partial diffs.
- Files: `judgment/PullRequestDigestJob.java`, `PullRequestDigestFailedException.java`; tests `PullRequestDigestJobTest.java`, `FakeJudgmentModel.java`.
- **Test first:** `PullRequestDigestJobTest.aSecondRunWithTheSameInputMakesNoCall`.
- Commit: `feat(F06): pull-request-digest job`

## Batch 3: tasks 9 to 12 (evidence builders and item tiers)

### Task 9. Digest reader and the new types
`PullRequestDigests` in `judgment`: the current digest of each PR by external id, with its judgment id. `EvidenceType` gains the five types, `SourceRole` gains `PULL_REQUEST_DIFF`, `ItemFields` gains the field shapes of design "New evidence types".
- Files: `judgment/PullRequestDigests.java`, `evidence/EvidenceType.java`, `SourceRole.java`, `ItemFields.java`; test `PullRequestDigestsTest.java`.
- **Test first:** `PullRequestDigestsTest.returnsOnlyTheCurrentDigestOfAPullRequest`.
- Commit: `feat(F06): digest reader and the new evidence types`

### Task 10. Test change builder
One item per human PR with a digest and a changed file matching `evidence.test_paths`; merge day; key `test_change:<prExternalId>`; fields with test kind and test files; sources PR, diff and linked work items; the digest id in the content hash.
- Files: `evidence/TestChangeBuilder.java`, `BuiltItem.java`; test `TestChangeBuilderTest.java`, `StoredRecordsFixture.java`.
- **Test first:** `TestChangeBuilderTest.buildsOneItemForAPullRequestThatChangesASpecFile`.
- Commit: `feat(F06): Test change builder`

### Task 11. Work type builder
For PRs with no resolved link, or only non-Bug links with no Feature above: Feature work no work item, Bug fix no work item, Refactor and performance, Security, Infra and release, Documentation, or nothing, by the digest's work type; Feature work of the work item itself for feature or enhancement once it is done (decision 149); a Bug link keeps only F05's Bug fix (decision 141); a PR under a Feature gives nothing new.
- Files: `evidence/WorkTypeItemBuilder.java`; test `WorkTypeItemBuilderTest.java`.
- **Test first:** `WorkTypeItemBuilderTest.aRefactorPullRequestWithNoWorkItemGivesARefactorItem`.
- Commit: `feat(F06): items from the pull request work type`

### Task 12. evidence-build runs the new builders and writes item tiers
`EvidenceBuildJob` runs the two builders; counts `pull_requests_without_digest`; for each Test change, Refactor, Security and Infra item written, an `item_tier` judgment on the new row (tier from `evidence.test_kind_tiers` or the digest's `workTypeTier`, reason, facts, model and prompt version copied, `based_on_judgment_id` the digest).
- Files: `evidence/EvidenceBuildJob.java`, `EvidenceItemStore.java`, `ItemTiers.java`; test `EvidenceBuildJobTest.java`, `ItemTiersTest.java`.
- **Test first:** `EvidenceBuildJobTest.writesATierJudgmentForANewTestChange`.
- Commit: `feat(F06): evidence-build builds the digest-based items with their tier`

## Batch 4: tasks 13 and 14 (API and web)

### Task 13. API: new types and the tier
`GET /api/developers` counts the new types; `GET /api/developers/{personId}/evidence-items` returns the new types with their fields and, where it has one, the current tier and its reason.
- Files: `api/TypeCounts.java`, `EvidenceItemResponse.java`, `DeveloperController.java`, `DeveloperEvidenceResponse.java`; test `DeveloperControllerTest.java`.
- **Test first:** `DeveloperControllerTest.listsATestChangeWithItsTier`.
- Commit: `feat(F06): developer API shows the new types and their tier`

### Task 14. Web: new types on the developer pages
`web/lib/evidence.ts` types; count badges for the new types on `/developers`; a section per new type on `/developers/[personId]` with PR link, date, tier badge and reason.
- Files: `web/lib/evidence.ts`, `web/lib/evidence.test.ts`, `web/app/developers/CountBadges.tsx`, `web/app/developers/[personId]/page.tsx`, a new section component, `developer.module.css`, `web/app/globals.css`, `page.test.tsx` files.
- **Test first:** `web/app/developers/[personId]/page.test.tsx` "shows a Test change section with its tier".
- Commit: `feat(F06): developer pages show the new types and their tier`
