# F06 JudgmentModel, Stage 1 PR digest, and the builders that need the PR work type: design

State: approved on 2026-11-22, through the question brief

## Goal

The first AI step. Every pull request of a human author gets a short, factual digest from a hosted model (Stage 1), stored as a `judgment`. From the digest's work type and test facts, `evidence-build` then builds the evidence items F05 could not: Test change, Refactor and performance item, Security item, Infra and release item, Documentation, and the Feature work and Bug fix items of pull requests that have no usable work item. Test, refactor, security and infra items get a tier.

## In scope / Out of scope

**In.**
- Flyway migration V9: table `judgment` with the append-only trigger (decision 52); the new evidence types in the `evidence_item.evidence_type` check; new `setting` rows.
- Package `judgment`: the `JudgmentModel` interface, one implementation for OpenAI-compatible chat completions APIs that tries an ordered list of free providers, each within its own limits (Groq, then Gemini; decisions 144, 146), the prompt files and their versions, the diff file filter, the judgment store with the skip-on-same-input-hash rule, the job `pull-request-digest`.
- Package `evidence`: the new builders, run inside the existing job `evidence-build`, and the tier judgments of the items they build.
- Readers of stored diffs in `source.github` (decisions 62, 89: built by their first user).
- Enabling the six architecture tests disabled "for F06" (`JudgmentModelBoundaryTest` x2, `AppendOnlyTablesTest`, `AppendOnlyCodeTest` x2, `SpecNumbersTest` for `judgment`).
- A measurement of time, tokens and rate limits on 50 real PRs, with an estimate for the one-month window, in `notes.md`.
- The real run digests the 172 human PRs of the two weeks already stored (1 to 14 Jul 2026), as far as the free limits allow (decision 145).
- The developer page shows the new item types with their tier (decision 151).

**Out.**
- Stage 2: complexity of Feature work and the judgment of review comments: F07. F06 builds Feature work items of single PRs but gives them no complexity and no tier.
- The item page, the blind check, prompt tuning: F08. The full-window run: F09 (its scope is open after decision 145).
- Several keys of one provider to get past its limits (decision 144).
- Tiers of Bug fix and Documentation items (master plan section 4): captured and counted only.
- Analytics, scoring, gates, the profile page: F10 to F12.
- A screen to correct a judgment (master plan section 4). The table supports corrections through `supersedes_id`.
- Coverage of changed lines (spec 6.5, Test change): not in the data.
- Everything in master plan section 4.

## Data and tables

### Rules copied from the sources

`poc-plan.md` 3.2, "The AI's two stages", Stage 1:
> One call per PR. Input: PR title, body, file list, and the diff with generated files, lock files and vendored code removed. Output: work type of the PR, modules touched, schema or data migration yes/no, new integration yes/no, nature of the change, tests included yes/no, test kind (unit, integration, regression, CI gate), a short reason. For the test change in a PR, and for a PR whose work type is refactor, performance, security or infra, it also gives a tier from the rows of spec 6.4, with a reason. A cheaper model is enough here.

`poc-plan.md` 3.2: "Every judgment stores model name, prompt version and a hash of the input, so it can be reproduced." "The only hand-written rules: the building rules below, the bot filter, the file filter for diffs, and the complexity-to-tier table."

`poc-plan.md` 3.2, building table, the rows F06 builds (the person on every item is the PR author):

| Evidence item | Built from | Tier |
| --- | --- | --- |
| Feature work, marked "no work item" | A PR with no work item whose work type is feature or enhancement | From complexity |
| Bug fix | (F05 builds the tracker part.) Also a PR with no work item whose work type is fix | None. Counted only |
| Test change | One item per PR that changes tests | From test kind: unit in own PR is T1, integration or regression is T2, a test gate in CI for a module is T3 |
| Refactor and performance item, Security item, Infra and release item | A PR with that work type whose work item is not under a Feature (for example type "Code maintenance", or no work item). A PR under a Feature stays in that Feature work group | From spec 6.4 |
| Documentation | A PR with no work item whose work type is docs | None. Counted only |
| No evidence item | A PR with no work item whose work type is maintenance, technical task, research or operational support. The spec has no evidence type for these. The PR keeps its work type and shows in Work classification | None |

Spec 6.2, "Work type": "The AI gives each PR and work item exactly one of 13 work types: feature, enhancement, fix, maintenance, research, technical task, operational support, refactor, test, docs, infra, performance, security. One type per item means each item is counted in exactly one place."

Spec 6.2, limits: "Size is not complexity. The AI must not treat a large change as hard work. [...] Lines of code are never used as a measure."

Spec 6.3: "L1 is about the person's own task. L2 is about their own feature. L3 is about a module or a team. L4 is about work across projects. When the AI or the lead meets a case that the table in 6.4 does not cover, they use this pattern to choose the tier." The AI sets the tier "from its content and links [...] the work type, which files were changed".

Spec 6.4, the rows F06 uses:

| Evidence type | T1 | T2 | T3 | T4, later |
| --- | --- | --- | --- | --- |
| Test change | Unit test in own PR | Integration or regression test | A test gate in CI for a module | Performance, load or security test |
| Refactor and performance item | Cleanup in own PR | Small refactor or speed-up on own initiative | Planned debt item, or a dependency kept current, or a measured bottleneck removed | Not used |
| Security item | Fix to a reported vulnerability in own code | Dependency fix or access-control fix | Security check added to the team pipeline | Not used |
| Infra and release item | Not used | Pipeline fix, Dockerfile, logging, or cutting a release (tag, branch, hotfix) | Team pipeline or monitoring | Not used |

Spec 6.5, the fields F06 stores:

| Evidence type | Fields |
| --- | --- |
| Test change | PR, linked task or fix, test kind (unit, integration, regression, CI gate), coverage of changed lines where CI reports it |
| Refactor and performance item | PR, work type, modules touched, dependency changes, measured before and after result where recorded |
| Security item | PR or item, kind (vulnerability fix, dependency upgrade, access control), vulnerability reference where known |
| Infra and release item | PR or tag, kind (pipeline, container, infrastructure code, monitoring, release tag, release branch, hotfix), scope (own feature or team) |
| Documentation | Files changed (README, docs folder, runbook, API doc), linked feature, scope (own work or team) |

`poc-plan.md` 5, risks: "Very large PRs. Diffs may exceed what one call can read. The file filter and the two-stage design handle most; the rest are marked 'judged on partial code'." "Cost. [...] Measured in build step 6 before the full run."

Master plan section 2: the model is reached only through `JudgmentModel`; every judgment stores reason, facts with source links, model, prompt version and input hash; the same input hash is never judged twice; `judgment` is append-only; lines of code are never given to the model for complexity; no number of the spec is a constant in code.

### Facts about the real data (Compose database, checked 22 Nov 2026)

The database holds F02's 215 PRs of 1 to 14 Jul 2026 (decision 91).
- 215 PRs: 43 by bots, 172 by humans. Of the human PRs, 133 have a resolved work item link, 14 only `not_visible` links, 25 no link at all.
- Of the 133 linked PRs: 61 link a Bug, 47 link a work item under a Feature (and no Bug), 25 link only work items with no Feature above them: 13 Documentation, 9 Code maintenance, 1 Implementation, 1 Milestone, 1 Task.
- 115 PRs change a file under `spec/` or `test/` or named `*_spec.rb`, `*.spec.ts`, `*.test.ts`.
- One `pull_request_diff` row per PR, `payload.files[]` with `filename`, `status`, `additions`, `deletions`, `changes`, `patch` (absent for binary or very large files), `sha`, `blob_url`, `raw_url`, `contents_url`.
- Patch size per PR (characters): median 4,960, 90th percentile 38,921, 99th 432,405, largest 1,110,018. 42 PRs exceed 20,000, 20 exceed 40,000, 8 exceed 100,000. All patches together: 5.7 million characters.
- Files per PR: median 4, 90th percentile 21, largest 504. 
- Heavy paths: `docs/` 2.4 million characters (user guide, admin guide, release notes); `config/locales/crowdin/*.yml` (machine translations, 149 files); `Gemfile.lock`, `package-lock.json` (65 files, 250,000 characters).
- PR body: median 754 characters, 90th percentile 5,379, largest 65,535.
- PR labels include `bugfix`, `feature`, `documentation`, `maintenance`, `security`, `dependencies`; they are not used (the model decides the work type).

### Groq Cloud (checked 22 Nov 2026 on console.groq.com/docs)

- OpenAI-compatible base URL `https://api.groq.com/openai/v1`, endpoint `chat/completions`.
- Production models: `llama-3.1-8b-instant`, `llama-3.3-70b-versatile`, `openai/gpt-oss-120b`, `openai/gpt-oss-20b` (context 131,072 tokens). Preview (not for production): `qwen/qwen3.8-27b` and others.
- JSON schema output with `strict: true` (constrained decoding, always matches the schema): `openai/gpt-oss-20b`, `openai/gpt-oss-120b`, `qwen/qwen3.8-27b`, `meta-models/muse-spark-open`. Strict needs every field required and `additionalProperties: false`. No streaming or tools with structured output.
- `reasoning_effort` `low`, `medium`, `high` for the gpt-oss models; reasoning is "part of the token output".
- Rate limits: the rate-limits page shows one table, for `openai/gpt-oss-120b`, `openai/gpt-oss-20b` and `qwen/qwen3.8-27b` each 30 requests per minute, 1,000 per day, 8,000 tokens per minute, 200,000 tokens per day. The page labels it the Developer plan, but the models page gives the same models 250,000 tokens and 1,000 requests per minute on the Developer plan. Read with decision 79 (third-party reports of the free tier: 6,000 to 12,000 tokens per minute, 100,000 to 500,000 per day), the table is most likely the free tier. The account's own limits are at console.groq.com/settings/limits (needs the user's login). Prices could not be read (groq.com/pricing showed no price table on 22 Nov 2026).
- Decision 144: free tiers only, several providers one after the other, each within its own limits; never several keys of one provider (Groq's AI policy forbids registering several accounts to exceed its limits, checked 22 Nov 2026). Second provider: the Gemini API free tier, OpenAI-compatible at `https://generativelanguage.googleapis.com/v1beta/openai/`, JSON schema output; its free limits are shown only in AI Studio.
- What this means: one request (prompt plus answer) cannot exceed 8,000 tokens on the free tier, and about 200,000 tokens a day is about 40 to 50 PRs a day (estimate below).

### Estimate for the July window (kept for F09)

Per PR call, in tokens (about 3.5 characters per token for code): instructions and schema about 1,500; title, body (cut at 4,000 characters) and file list about 600; the diff up to the budget; the answer with low reasoning about 800. With a diff budget of 12,000 characters (about 3,500 tokens) a call is at most about 6,500 tokens and about 4,000 on average. July has about 450 PRs, about 360 by humans (decision 78, and 172 of 215 human in two weeks): about 1.5 million tokens. On the free tier that is 7 to 8 days of the daily limit, and about 6 hours at 8,000 tokens per minute. The 50-PR measurement replaces this estimate with measured numbers.

### Table `judgment` (migration V9)

Never updated, never deleted (trigger as on `evidence_item`). A correction or a new judgment of the same subject is a new row with `supersedes_id`.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint identity | |
| `kind` | text, check | `pull_request_digest` (Stage 1), `item_tier` (the tier of a Test change, Refactor and performance, Security or Infra and release item). F07 adds its kinds to the check |
| `raw_record_id` | bigint, FK `raw_record`, null | The subject when it is a PR: the exact `pull_request` record version judged |
| `evidence_item_id` | bigint, FK `evidence_item`, null | The subject when it is an evidence item: the exact item row |
| | check | exactly one of the two subjects is set |
| `value` | jsonb | The answer. For a digest: the digest object below. For an item tier: the facts the tier rests on (test kind, kind, scope) |
| `tier` | text null, check `T1` to `T4` | The tier, when the kind gives one |
| `reason` | text not null, not blank | |
| `facts` | jsonb not null | Array of `{ "fact": text, "link": url }`, at least one entry; every link is made by the system (decision 136) |
| `confidence` | numeric null | Not used by F06; F07 decides |
| `judged_by` | text, check `model`, `person` | |
| `model` | text null | The model id the API answered with; required when `judged_by = 'model'` |
| `prompt_version` | text null | Such as `pull-request-digest/v1`; required when `judged_by = 'model'` |
| `input_hash` | text not null | SHA-256 of the canonical JSON of the input (decision 157) |
| `based_on_judgment_id` | bigint FK `judgment`, null | For an `item_tier`: the digest it was read from |
| `supersedes_id` | bigint unique FK `judgment`, null | |
| `judged_at` | timestamptz not null | |

Unique index on `(kind, input_hash)` where `judged_by = 'model'`: the same input is never judged twice, in the database as well as in code.

The current judgment of a subject and kind is the row no other row supersedes.

### The digest (`value` of a `pull_request_digest`)

```
workType               one of the 13 work types of spec 6.2
modulesTouched         list of text (OpenProject module or top-level area, such as "backlogs", "meeting", "core")
schemaOrDataMigration  yes/no
newIntegration         yes/no
natureOfChange         text, one or two sentences
testsIncluded          yes/no (the model's view; the path rule decides a Test change item, decision 148)
testKind               none | unit | integration | regression | ci_gate
workTypeTier           for work type refactor, performance, security, infra: { tier T1 to T3, reason }; else "not applicable"
securityKind           vulnerability_fix | dependency_upgrade | access_control | not_applicable
infraKind              pipeline | container | infrastructure_code | monitoring | release_tag | release_branch | hotfix | not_applicable
infraScope             own_feature | team | not_applicable
dependencyChanges      yes/no
documentationScope     own_work | team | not_applicable
reason                 text
facts                  list of { fact, filePath or "" }
judgedOnPartialCode    yes/no, set by the system, not the model (decision 133)
```

The schema is strict: every field required, no other field. The answer is checked again at the boundary (decision 135).

### New `setting` rows (V9, valid from 2026-Q2)

- `judgment.diff_excluded_paths`: list of path patterns whose patch is not sent (the file is still named in the file list). Seeded: lock files (`Gemfile.lock`, `package-lock.json`, `yarn.lock`, `*.lock`), machine translations (`config/locales/crowdin/`, `modules/*/config/locales/crowdin/`), vendored code (`vendor/`, `frontend/vendor/`), minified and generated files (`*.min.js`, `*.min.css`, `*.map`, `db/structure.sql`). Checked against the real file names on 22 Nov 2026.
- `evidence.test_paths`: path patterns that make a file a test file (`spec/`, `test/`, `tests/`, `*_spec.rb`, `*.spec.ts`, `*.test.ts`, `modules/*/spec/`). Decision 148.
- `evidence.test_kind_tiers`: `{"unit":"T1","integration":"T2","regression":"T2","ci_gate":"T3"}`. Decision 147.

### New evidence types (`evidence_item.evidence_type`)

`test_change`, `refactor_performance`, `security`, `infra_release`, `documentation`, besides F05's three. Keys (decision 138):
- `test_change:<prExternalId>`, `refactor_performance:<prExternalId>`, `security:<prExternalId>`, `infra_release:<prExternalId>`, `documentation:<prExternalId>`
- Feature work of a PR with no work item: `feature_work:pull_request:<prExternalId>`
- Bug fix of a PR with no work item: `bug_fix:pull_request:<prExternalId>`
- Feature work of a work item with no Feature above it (decision 149): `feature_work:<itemNumber>:<personId>`, the F05 key format with the item standing in for the Feature.

`<prExternalId>` is `opf/openproject#24204`. Project: none for a PR-based item; the item's project for Feature work of a work item (decision 139). Source link: the PR on GitHub, or the work item on OpenProject.

Fields (`fields` JSONB), built in `ItemFields`:
- Test change: `pullRequest` {number, title, url}, `linkedWorkItems` [{number, title, type, url}], `testKind`, `testFiles` [path].
- Refactor and performance: `pullRequest`, `workType` (refactor or performance), `modulesTouched`, `dependencyChanges`, `linkedWorkItems`.
- Security: `pullRequest`, `kind`, `vulnerabilityReference` (null; not in the digest), `linkedWorkItems`.
- Infra and release: `pullRequest`, `kind`, `scope`, `linkedWorkItems`.
- Documentation: `pullRequest`, `files` [path], `scope`, `linkedWorkItems`.
- Feature work, no work item: `noWorkItem: true`, `pullRequests` [one], `workType`.
- Bug fix, no work item: `noWorkItem: true`, `pullRequests` [one].
- Feature work of a work item: as F05 (`feature` is the item, `tasks`, `pullRequests`, `doneAt`).

Every item lists its sources in `evidence_item_source`: the PR record (role `pull_request`), the diff record (new role `pull_request_diff`), and the work item records (role `work_package`) where it has any. The digest judgment is not a source: it is a judgment, and the item's hash includes the digest's id (decision 140), so a new digest gives a new item row.

## Main flows

### 1. Job `pull-request-digest` (Stage 1)

Started with `POST /api/jobs/pull-request-digest/runs`, body `{}` or `{"pullRequestNumbers":"24204,24201"}` (repository `opf/openproject` is implied by the stored external ids; the numbers select stored PRs). Run after `github-import`; it needs no OpenProject data.

1. Read every stored PR (decision 145: the stored two weeks), the latest version of each (`StoredPullRequests.all()`), or the listed ones. Leave out a PR whose author is a bot (`BotAccounts.isBot`, quarter of the run day, decision 84) or that has no author (decision 126); count them.
2. For each PR, oldest merge first, in its own transaction:
   1. Read its latest `pull_request_diff` record. A PR with no diff record fails (counted, named at the end).
   2. Build the input: title, body (cut at `ei.judgment.max-body-characters`), the file list (path and status, no line counts, D132), and the patches: files matching `judgment.diff_excluded_paths` or without a patch are listed by name only; the rest are added whole in the order source files, test files, docs files, each group in GitHub's order, until `ei.judgment.max-diff-characters` (decision 133). A patch that does not fit is left out and named; when any patch is left out, `judgedOnPartialCode` is yes.
   3. Compute the input hash (decision 157). If a `pull_request_digest` with this hash exists, skip (count `skipped_same_input`). No call.
   4. Call `JudgmentModel.judge` (the provider chain, decision 146) with the prompt `pull-request-digest/v1`, the input and the JSON schema.
   5. Check the answer (decision 135). Build the facts with links (decision 136). Store one `judgment` row: kind `pull_request_digest`, subject the PR record version, `tier` null, `supersedes_id` the current digest of the same PR if there is one.
3. Counts on the run: `pull_requests_read`, `bot_pull_requests_skipped`, `records_without_author`, `skipped_same_input`, `digests_written`, `judged_on_partial_code`, `model_calls`, `input_tokens`, `output_tokens`, `rate_limit_wait_seconds`, `pull_requests_failed`, and `pull_requests_left_for_later`, and per provider `calls_<name>` (decision 146).
5. When every provider has reached its limit (`ModelLimitReachedException`), the run stops: the PRs not yet judged are counted as `pull_requests_left_for_later` and the run succeeds; the next run goes on, skipping the PRs already judged (decision 152).
4. Failures: one PR's failure (bad answer after the retries, missing diff) is counted and the run goes on; the run fails at the end naming the first 20 PRs (decision 90 rule).

### 2. `JudgmentModel` and the OpenAI-compatible client

```
public interface JudgmentModel {
    /** Sends one request to the model and returns its structured answer. */
    ModelAnswer judge(ModelRequest request);
}
record ModelRequest(String promptVersion, String instructions, String input, String schemaName, JsonNode jsonSchema)
record ModelAnswer(JsonNode answer, String model, long inputTokens, long outputTokens)
```

- `ProviderChain` implements `JudgmentModel`: it holds the configured providers in order and sends each request to the first one that has not reached its limit in this run. A 429 whose wait (`retry-after` or `x-ratelimit-reset-*`) is longer than `ei.judgment.max-rate-limit-wait` marks that provider as at its limit for the rest of the run; a shorter wait is waited out. When no provider is left it throws `ModelLimitReachedException` (decision 146).
- `OpenAiCompatibleClient`, one per provider, sends `POST {base-url}/chat/completions` with the model, a system message (the prompt), a user message (the input), `response_format: {type: json_schema, json_schema: {name, strict: true, schema}}`, `reasoning_effort` and `temperature` from configuration, through Spring's `RestClient` (decision 156).
- Configuration (`ei.judgment.providers`, from `.env`, decision 158): provider 1 `LLM_PROVIDER_1_NAME` (default `groq`), `LLM_PROVIDER_1_BASE_URL` (default `https://api.groq.com/openai/v1`), `LLM_PROVIDER_1_API_KEY`, `LLM_PROVIDER_1_MODEL` (default `openai/gpt-oss-120b`, decision 153), `LLM_PROVIDER_1_REASONING_EFFORT` (default `low`); provider 2 the same names with `_2_` (default name `gemini`, base URL `https://generativelanguage.googleapis.com/v1beta/openai`, no default model, no reasoning effort). A provider with no key is left out; no provider with a key fails the run at its start. A key is never logged. Operating numbers are configuration properties (decision 45): timeout, retries, retry wait, `max-rate-limit-wait`, `max-diff-characters` (12,000, decision 155), `max-body-characters` (4,000).
- Rate limits: the client reads Groq's `retry-after` on a 429 and the `x-ratelimit-remaining-tokens` and `x-ratelimit-reset-tokens` headers, and waits before the next call when the next request would not fit. Calls are made one at a time (decision 134).
- A 413 (request too large), a 400 schema mismatch, or a 5xx after the retries is an exception naming the PR and the status, never the key.

### 3. Prompts

- One file per prompt and version: `backend/src/main/resources/judgment/prompts/pull-request-digest-v1.md`. The version is `pull-request-digest/v1`.
- The prompt gives: the 13 work types with one line each (spec 6.2), the four Test change, Refactor and performance, Security and Infra and release rows of spec 6.4 and the L1 to L4 pattern of spec 6.3, the rule that size is not complexity, and the instruction to quote only what the PR shows.
- A test pins the SHA-256 of each released prompt file (decision 129): changing a prompt means adding a new version file.

### 4. `evidence-build`, extended

After F05's three builders, the run reads the current digest of every human PR (`PullRequestDigests`, a reader in `judgment` that `evidence` uses) and applies these rules to each PR with a digest. A PR with no digest yet gives none of the F06 items (count `pull_requests_without_digest`).

| PR | Item built |
| --- | --- |
| Any human PR with a changed file matching `evidence.test_paths` (decision 148) | Test change |
| No resolved work item link; work type feature or enhancement | Feature work, no work item |
| No resolved link; fix | Bug fix, no work item |
| No resolved link; refactor or performance | Refactor and performance item |
| No resolved link; security | Security item |
| No resolved link; infra | Infra and release item |
| No resolved link; docs | Documentation |
| No resolved link; maintenance, technical task, research, operational support, test | None (the Test change row may still apply) |
| Links only non-Bug items with no Feature above them; refactor, performance, security, infra | That item, as above |
| Links only non-Bug items with no Feature above them; feature or enhancement | Feature work of that work item, `feature_work:<itemNumber>:<personId>`, once the item has reached a done status, dated by its first done day (decision 149, as decisions 112 and 113) |
| Links only non-Bug items with no Feature above them; fix, docs, maintenance, technical task, research, operational support, test | As a PR with no work item: Bug fix, Documentation, or none (decision 149) |
| Links a work item under a Feature | Nothing new: F05's Feature work group (a refactor PR there is not a Refactor item) |
| Links a Bug | F05's Bug fix; its other, non-Bug links follow the rows above (decision 141) |

Every item built from one PR is dated by the PR's merge day (decision 150). A human author with no person fails the item (decision 102).

For each Test change, Refactor and performance, Security and Infra and release item written (new or superseding row), `evidence-build` writes an `item_tier` judgment on that item row (decision 154): for a Test change the tier of its test kind in `evidence.test_kind_tiers` (decision 147; a test kind `none` from the model gives T1 with the reason that the model saw no test kind), from the digest's `workTypeTier` for the others; reason, facts, model and prompt version copied from the digest; `based_on_judgment_id` the digest; input hash over the digest id and the item's content hash. An unchanged item gets no new judgment.

### Uses (from earlier features)

- `StoredPullRequests.all()`, `find(String repository, int number)` and `PullRequestFacts.of(RawRecord)` (`rawRecordId`, `externalId`, `number`, `title`, `url`, `mergedAt`, `Optional<GitHubAuthor> author`) in `source.github`.
- `RawRecordStore.findLatest(String dataSourceKey, String recordKind, String externalId)`, record kind `pull_request_diff` (`GitHubRecordKind.PULL_REQUEST_DIFF`).
- `BotAccounts.isBot(String login, String typename, Quarter quarter)`, `PersonDirectory.personOfGitHubAccount(long databaseId)`.
- `StoredWorkPackages.linksOf(String pullRequestExternalId)`, `facts(long)`, `nearestFeature(long)`, `firstDoneAt(long, Quarter)`; `WorkPackageFacts(rawRecordId, number, title, type, isBug, projectId, priority, url)`.
- `LinkedPullRequests.read(Quarter)`, `BuiltItem(key, type, personId, projectId, occurredOn, sourceUrl, fields, sources)`, `EvidenceItemStore.write(BuiltItem)` returning `WriteOutcome`, `EvidenceItemRepository.findCurrentByKey`.
- `SettingService.getTextList`, `getJson`; `CanonicalJson.sha256Hex(JsonNode)`; `Job`, `JobArguments`, `JobCounts.add`; `FailedItems.named`.

### Gives (to later features)

- `JudgmentModel` (`judge(ModelRequest): ModelAnswer`) and its configured bean; F07 adds its prompts and models.
- `JudgmentStore`: write a judgment, find the current judgment of a subject and kind, refuse a model judgment whose input hash exists.
- `PullRequestDigests`: the current digest of a PR by external id, as a typed `PullRequestDigest` record. Stage 2 reads it.
- `DiffInput` (the file filter and budget) for F07's review comment hunks if needed.
- Table `judgment` with kinds `pull_request_digest` and `item_tier`.

## Edge cases

- A PR with no diff record: fails, named; a re-run of `github-import` stores it.
- A diff whose every patch is excluded or absent (all binary, all lock files): judged on title, body and file list; `judgedOnPartialCode` yes if any patch was left out for size, no if only excluded paths were left out.
- A request larger than the tokens-per-minute limit (413): the diff budget is too high for the plan; fails that PR naming the cause.
- The model returns a value outside an enum or a missing field (should not happen with strict mode): retried once, then the PR fails.
- A new version of the PR or diff record (re-import): a new input hash, so a new digest that supersedes the old one, then new item rows and new tier judgments.
- A new prompt version or model: a new input hash for every PR; the next run judges all again and keeps the old judgments.
- The same PR in a Feature work group and with work type refactor: stays in the group only (F05 decision 115).
- A PR whose digest says work type fix but links a Feature task: stays in the Feature work group (the tracker decides; F06 reads work type only for PRs with no usable link).
- A bot PR: no digest and no item.
- A PR with no GitHub author: no digest, no item, counted (decision 126).
- Every provider at its limit mid-run: the run stops and counts what is left (decision 152).
- One provider at its limit: the next provider judges the rest; each judgment stores its model.
- A digest that names a file path not in the PR: the fact keeps the PR link only.

## How it will be tested

Tests never call the real model: they use a fake `JudgmentModel` that returns fixed answers and counts calls; the HTTP client is tested against a mock server (as `GitHubApiFixture`).

Master plan "Done when":
1. "Every judgment row has a reason, at least one source link, a model and a prompt version." Store tests refuse a row without them; architecture tests (enabled) check the trigger and the entity; after the real run, a SQL count of rows missing any of them is 0.
2. "A second run with the same input makes no call." Job test: second run with the fake model makes 0 calls and writes 0 rows; on real data, the second run's `model_calls` is 0.
3. "The time, tokens and rate limits met for 50 PRs are written into `notes.md`, with an estimate for the one-month window." A real run with `pullRequestNumbers` of 50 PRs (chosen by the session to cover small, median and large diffs), its counts and duration in `notes.md`. Then a run without arguments over all 172 human PRs, as far as the limits go (decision 145). Needs `LLM_PROVIDER_1_API_KEY` (and 2) in `.env`.
4. "A diff too large for one call is judged on what fits and marked 'judged on partial code'." Unit test of the diff input on a diff over the budget; on real data, the count of digests with `judgedOnPartialCode` equals the job's count.
5. Goal line: "Every PR has a digest; test, refactor, security and infra items exist with a tier." SQL after the runs: human PRs without a current digest = 0; every current Test change, Refactor, Security and Infra item has one current `item_tier` judgment with a tier.

Other tests:
- The file filter: each seeded pattern on a real file name; excluded files still in the file list; no line counts in the input.
- The input hash changes with the prompt version, the model, the PR record and the diff record; not with the run day.
- The provider chain: a long 429 moves to the next provider; no provider left throws `ModelLimitReachedException`; a provider without a key is left out.
- The client: request body (model, schema, strict, reasoning effort), 429 with `retry-after` waits, rate-limit headers pace the next call, 413 and 400 fail with the status and without the key.
- Each building rule row of "Main flows" 4, with a fixture PR, including the Bug and Feature rows that must build nothing new.
- Tier of a Test change per test kind; tier of a Refactor item from the digest.
- The prompt file hash test.
- The six architecture tests enabled.
- Web (decision 151): the developer page shows each new type with its tier.

## Decisions

The user's answers (22 Nov 2026): Q1 own answer (several free keys, free alternatives, minimal processing: "the idea is to prove AI can judge"); after a follow-up, F1 "all the PRs ingested for two weeks, see how far we can go", F2 and Q2 to Q11 as recommended. The brief and the follow-up are in the git history of this file (commits `add7375`, `d1aeba7`).

- 127. Table `judgment` (V9), subject a PR record version or an evidence item row, append-only, unique per kind and input hash for model judgments.
- 128. Replaced by 157.
- 129. Versioned prompt files, hash pinned by a test.
- 130. Every human PR gets a digest; bot PRs and PRs without an author get none.
- 131. File filter as the setting `judgment.diff_excluded_paths`; filtered files still named.
- 132. No line counts sent to the model.
- 133. Over the budget: source, then test, then docs files, whole patches; `judgedOnPartialCode` set by the system.
- 134. One call at a time, paced by rate-limit headers; operating numbers as configuration. Its `.env` names are replaced by 158.
- 135. Strict JSON schema, checked on arrival; one retry, then the PR fails and the run goes on.
- 136. Fact links made by the system, never by the model.
- 137. Job `pull-request-digest`, no arguments or `pullRequestNumbers`; tokens, calls and waits counted.
- 138. New evidence types and keys.
- 139. Items of one PR have no project; Feature work of a work item has its project.
- 140. An item's hash includes its digest; the diff record is a source (`pull_request_diff`).
- 141. A Bug link keeps its Bug fix and gives no Refactor, Security or Infra item.
- 142. Tests use a fake model and a mock server.
- 143. `PullRequestDigests` lives in `judgment`; only `judgment` sees `JudgmentModel`.
- 144. Q1: free tiers only; several providers one after the other, each within its own limits; never several keys of one provider.
- 145. F1: digest all 172 human PRs of the stored two weeks, as far as the free limits go.
- 146. F2: an ordered provider chain (Groq, then Gemini); a long 429 moves to the next provider.
- 147. Q2 A: a Test change's tier comes from its test kind (`evidence.test_kind_tiers`).
- 148. Q3 A: a path rule (`evidence.test_paths`) makes a PR a Test change.
- 149. Q4 A: a PR linking only non-Bug items with no Feature above follows the no-work-item rules, except feature or enhancement, which gives Feature work of that item once done.
- 150. Q5 A: items of one PR are dated by the merge day.
- 151. Q6 A: the developer page shows the new types with their tier.
- 152. Q7 A: when every provider is at its limit, the run stops and counts what is left.
- 153. Q8 A: `openai/gpt-oss-120b` on Groq; Gemini's model is set in `.env`.
- 154. Q9 A: an `item_tier` judgment on each Test change, Refactor, Security and Infra item.
- 155. Q10 A: 12,000 characters of diff per call (free tiers), a configuration value.
- 156. Q11 A: Spring `RestClient`, no new dependency.
- 157. Replaces 128: the input hash covers the kind, the prompt version, the input text and the source record ids, not the model. Judging again with another model needs a new prompt version.
- 158. Replaces the names of 134: `LLM_PROVIDER_<n>_NAME`, `_BASE_URL`, `_API_KEY`, `_MODEL`, `_REASONING_EFFORT` for n = 1, 2.
