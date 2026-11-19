# F02 GitHub connector: plan

Spec: `docs/features/F02-github-connector/design.md` (approved 2026-11-19). A short plan (decision 40): the design carries the detail.

Branch `f02-github-connector`. Every task: test first, `./mvnw spotless:apply`, `./mvnw verify` green, one commit. Packages: `job` for the mechanism, `source.github` for the connector.

## Tasks

**1. Tests never read `.env`.**
The test application context loads no property source from a `.env` file; the backend outside tests still does.
Files: `backend/src/main/resources/application.yml`, test support as needed.
First test: `TestEnvironmentTest.aTestContextLoadsNothingFromTheEnvFile` — no property source of the test environment comes from a `.env` file.
Commit: `test(F02): tests no longer read the developer's .env`

**2. NUL characters are escaped in every raw record.**
Migration `V3__raw_record_nul_escaped.sql` adds `nul_escaped`. `RawRecordStore.store` replaces U+0000 in keys and values with `\u0000` before hashing, sets the flag; `RawRecord.isNulEscaped()`.
Files: migration, `JpaRawRecordStore`, `RawRecord`, `RawRecordStoreTest`.
First test: `RawRecordStoreTest.aNulCharacterIsStoredEscapedAndTheRowIsMarked`; then the same payload stored again is `UNCHANGED`.
Commit: `feat(F02): escape the NUL character in raw record payloads`

**3. `job_run` table and its entity.**
Migration `V4__job_run.sql` (design 6.1: columns, status check, `finished_at` check, unique partial index on running). `JobRun` entity and repository; `DatabaseTest` empties `job_run`.
Files: migration, `job/JobRun`, `job/JobRunRepository`, `job/JobRunStatus`, `DatabaseTest`, `SchemaTest`.
First test: `JobRunRepositoryTest.aSecondRunningRunOfTheSameJobIsRefusedByTheDatabase`.
Commit: `feat(F02): job_run table`

**4. Job interface, arguments and the launcher.**
`Job` gains `checkArguments` and `run(arguments, counts)`; `JobCounts`; `JobArguments` implementation from a JSON object of strings. `JobLauncher.start(name, arguments, startedBy)`: unknown job, bad arguments, already running, background run, counts flushed every `ei.jobs.count-flush-interval` and at the end, `succeeded` or `failed` with message. Running rows become `interrupted` at application start.
Files: `job/Job`, `job/JobCounts`, `job/JobArguments`, `job/MapJobArguments`, `job/JobLauncher`, `job/JobRunCounts`, `job/InterruptedRunMarker`, `job/JobsProperties`, `JobConfiguration`, `JobRegistry` (now used).
First test: `JobLauncherTest.aRunIsRecordedAsRunningThenSucceededWithItsCounts`; then failure, refused second run, interrupted at start, bad arguments recorded nothing.
Commit: `feat(F02): job launcher that records every run`

**5. The job endpoint, local callers only.**
`POST /api/jobs/{name}/runs` (202 with `Location`, 400, 404, 409), `GET /api/jobs/runs/{id}` (200, 404). A filter refuses any non-loopback caller on `/api/jobs/**` with 403. Errors as problem details naming the value.
Files: `job/JobController`, `job/JobRunResponse`, `job/LocalCallerOnlyFilter`, `job/JobExceptionHandler`.
First test: `JobControllerTest.startingAJobAnswers202WithTheRunAndItsLocation`; then each error code; `LocalCallerOnlyFilterTest.aCallerFromAnotherMachineIsRefused`.
Commit: `feat(F02): endpoint that starts a job and shows a run`

**6. Setting `github.bot_logins`.**
Migration `V5__github_bot_logins.sql`, valid from `2026-Q2`, the five logins of design 6.1.
First test: `GitHubBotLoginsSettingTest.theBotLoginsAreReadableForTheWindowQuarters` (2026-Q2 to 2026-Q4).
Commit: `feat(F02): setting with the GitHub bot logins`

**7. GitHub API client.**
`GitHubApi` over `RestClient`: GraphQL POST, REST GET following `Link` pages, bearer token from `GITHUB_TOKEN` (never logged), one call at a time. Rate limit: wait for the reset when the remaining budget is below `ei.github.rate-limit-reserve`, honour `retry-after`. Transient errors retried `ei.github.retries` times. `401` or `403` without a rate-limit sign throws `GitHubTokenRefusedException`. GraphQL `errors` throw with the message. Missing token: `GitHubTokenMissingException`. Waiting goes through an injected sleeper so tests do not wait.
Files: `source/github/GitHubApi`, `GitHubProperties`, `GitHubConfiguration`, exceptions, `Sleeper`.
First test (MockRestServiceServer): `GitHubApiTest.aGraphQlCallSendsTheTokenAndReturnsTheParsedData`; then paging, waiting at the reserve, retry then give up, refused token, GraphQL errors, the token never in an exception message.
Commit: `feat(F02): GitHub API client with rate limit and retries`

**8. List merged PRs by merge date.**
`MergedPullRequestSearch.numbers(repository, from, to)`: one search per day, 100 a page, fails on `incomplete_results` after retries and on a day above 1,000.
Files: `source/github/MergedPullRequestSearch`, `github/search-merged.graphql`.
First test: `MergedPullRequestSearchTest.aRangeIsSearchedOneDayAtATimeAndAllPagesAreRead`.
Commit: `feat(F02): list merged pull requests by merge date`

**9. Fetch one PR with everything.**
`PullRequestFetcher.fetch(repository, number)` returns `FetchedPullRequest` (PR node with all files, reviews, review comments, conversation comments, diff files, truncated) or not found / not merged. Every connection read to the end; `pageInfo` and `totalCount` stripped; JSON parsed with `CanonicalJson.parse`. Recorded responses from PR 24204 in test resources.
Files: `source/github/PullRequestFetcher`, `FetchedPullRequest`, `github/pull-request.graphql` and the page queries.
First test: `PullRequestFetcherTest.aPullRequestIsFetchedWithAllItsParts`; then more than one page of each connection, more than 3,000 files, a file without patch, not merged, not found, deleted author.
Commit: `feat(F02): fetch a pull request with its files, reviews and comments`

**10. Store one PR as its records.**
`PullRequestRecorder.store(FetchedPullRequest)`: the five record kinds of design 6.2 with external ids and URLs, in one transaction; returns the outcome per record.
Files: `source/github/PullRequestRecorder`, `GitHubRecordKind`.
First test: `PullRequestRecorderTest.aPullRequestIsStoredAsOneRowPerPart`; then stored again is all `UNCHANGED`, an edited body makes version 2 of the PR row only, a new comment adds one row.
Commit: `feat(F02): store a pull request as raw records`

**11. The `github-import` job.**
`GitHubImportJob`: checks the arguments (design 5.2), lists or takes the numbers, fetches and stores each PR, counts per kind and outcome, a failed PR does not stop the run, the run fails at the end naming the failed PRs, and fails at once on a listing failure or a refused token.
Files: `source/github/GitHubImportJob`, `GitHubImportArguments`.
First test: `GitHubImportJobTest.aRangeIsImportedAndCounted`; then argument errors, one PR failing, refused token, missing token.
Commit: `feat(F02): github-import job`

**12. End to end through the endpoint.**
With GitHub served from recorded responses: start `github-import` over HTTP, wait for the run, check rows and counts; run again, nothing added; a changed body, a new version with the old one kept.
Files: `source/github/GitHubImportEndToEndTest`.
Commit: `test(F02): github-import end to end through the job endpoint`

**13. First real run and verification.**
The user puts `GITHUB_TOKEN` in `.env`. Run 1 to 14 Jul 2026, check design section 10 (count against GitHub search, second run adds nothing), record the evidence and measurements in `notes.md`. Update `CLAUDE.md` commands (how to start a job), master plan status, `progress.md`, `decisions.md`.
Commit: `docs(F02): verification and close`

## Verify the feature

```
export JAVA_HOME=~/.jdks/temurin-25.0.5
cd backend && ./mvnw spotless:apply && ./mvnw verify
```

Then design section 10 on real data, and CI green on the pull request.
