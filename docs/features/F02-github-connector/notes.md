# F02 GitHub connector: notes

## Result

All 13 tasks of `plan.md` are built, one commit per task, test first. 248 backend tests pass. The first real import of 1 to 14 Jul 2026 stored all 215 merged pull requests; a second run added no rows. The "Done when" evidence is at the end of this file. Still open before "done": the pull request and a green CI run.

## Differences from the plan and the design, and why

| Where | Plan or design | Built | Why |
| --- | --- | --- | --- |
| Task 1 | "Tests never read `.env`", mechanism left open | The `.env` import in `application.yml` sits in a document active only on profile `!test`; `DatabaseTest` sets `@ActiveProfiles("test")` | A test property cannot remove an import declared in `application.yml`; a profile can |
| Task 2 | Not stated | `SchemaTest.flywayAppliedVersionsOneAndTwo` became `everyMigrationFileIsAppliedSuccessfullyAndInOrder`; it compares the history with the `V*__*.sql` files on the classpath | The old test pinned the list and broke on every new migration |
| Task 3 | Not stated | `SchemaTest.theSixBaseTablesExist` became `theTablesOfTheMigrationsExist` and lists `job_run` | Same reason |
| Task 4 | `JobRunCounts` written "every interval" | `RecordedJobCounts` writes on an `add` once the interval has passed since the last write, and at the end. No timer thread | A run that waits (rate limit) has no new counts to write; no extra thread to stop |
| Task 4 | Executor not stated | `JobConfiguration` holds one virtual-thread executor, not a bean, shut down with `shutdownNow` on stop | A bean of type `Executor` would make Spring Boot drop its own task executor |
| Task 4 | Not stated | A `Clock` bean in `common/ClockConfiguration` | The launcher and the marker read the time from it; tests fix it |
| Task 4 | Interrupted runs marked "at start" | `InterruptedRunMarker` implements `SmartInitializingSingleton`, which runs before the web server takes requests | `ApplicationReadyEvent` comes after the server starts, so a new run could be marked interrupted by mistake |
| Task 6 | Not stated | `DatabaseTest` reads the `setting` rows once from the freshly migrated database and puts them back after each truncate | Without this every test wiped the seeded `github.bot_logins`; F03 and F11 seed settings too |
| Task 7 | Rate limit read from GraphQL `rateLimit` and REST headers | Headers only (`x-ratelimit-remaining`, `x-ratelimit-reset`, `retry-after`) | GitHub sends the same headers on GraphQL answers; one rule for both. A rate-limited answer without any time waits one minute, as GitHub advises |
| Task 7 | Not stated | A `Link` next page outside the API root is refused | The token must never be sent to another host |
| Task 8 | Search as in design | GraphQL search; "incomplete" means fewer results than `issueCount`, not REST's `incomplete_results` | GraphQL search costs GraphQL points, not the 30-a-minute REST search limit, and has no `incomplete_results` field |
| Task 9 | Not stated | Each GraphQL fragment lives once in `github/fragments/`; `GraphQlDocuments` adds every fragment a query uses (also through other fragments) | The first page and every further page of a part ask for exactly the same fields, so their hashes agree |
| Task 9 | Not stated | A review thread's `id` is requested to read further pages of its comments; threads are not stored | Only way to page a thread's comments |
| Task 9 | `truncated` when more than 3,000 files | `truncated` when the REST list has fewer files than the GraphQL list | Data-driven; no copy of GitHub's limit in code |
| Task 11 | Not stated | `GitHubImportArguments` refuses unknown argument names and lists the known ones | A mistyped name would otherwise be ignored |

None of these changes what F02 does.

## What a later feature should know

- **Start a job:** `curl -s -X POST localhost:8080/api/jobs/<name>/runs -H 'Content-Type: application/json' -d '{...}'`. Every value is text. The answer is `202` with the run and its `Location`; follow it with `GET /api/jobs/runs/<id>`. Only calls from the same machine are accepted.
- **A new job:** a `@Component` implementing `Job` with `name()`, `checkArguments(JobArguments)` (throw `IllegalArgumentException` that names the argument) and `run(JobArguments, JobCounts)`. Count with `counts.add(name, amount)`.
- **F03 fetches PRs by number:** `{"repository":"opf/openproject","pullRequestNumbers":"24204,24201"}`. F03 also builds the readers of the stored GitHub rows (decision 46).
- **Row layout:** design 6.2. Look a PR up with `record_kind = 'pull_request'` and `external_id = 'opf/openproject#24204'`. Reviews and comments carry `pullRequest.number` and `pullRequest.repository.nameWithOwner` in their payload.
- **The bot list** is the setting `github.bot_logins`; a GraphQL `__typename` of `Bot` also means a bot. `dependabot` is named without `[bot]` in GraphQL.
- **Changing a GraphQL query or fragment** changes the hash of every record it touches, so the next run writes a new version of all of them. Do it only with a decision.
- **Tests:** never call GitHub. Use `GitHubApiFixture` (a `GitHubApi` on a mock server), `GitHubResponses` (small answers) and `RecordedPullRequest24204` (real answers). `JobEndpointClient` calls the job endpoint like `curl`. A test that needs a migration-seeded setting gets it from `DatabaseTest`.
- **Tests run with the profile `test`,** which never reads `.env`.
- **`DatabaseTest` empties `job_run` too.** A feature that adds a table adds it to that `truncate` line.

## Not covered by a test

- A real stop of the backend during a run, followed by a start: the marker is tested by calling it, not by stopping the process.
- The `409` against the real backend: covered by `JobControllerTest` with a test job.
- A rate-limit wait against real GitHub: the run used 2 to 3 points per pull request and never reached the reserve.

## Measurements (19 Nov 2026)

- Run 1, 1 to 14 Jul 2026: 6 min 22 s for 215 pull requests (1.8 s each), one call at a time. No warnings, no rate-limit wait.
- Run 2, same range: 5 min 54 s, everything unchanged. A re-run fetches everything again; it saves writes, not calls.
- Rows: 215 `pull_request`, 215 `pull_request_diff`, 674 `review`, 735 `review_comment`, 367 `pull_request_comment`; 2,206 in all. `raw_record` takes 6 MB.
- Estimate for the whole window (2,377 pull requests): about 70 minutes and 70 MB.
- 25 distinct PR authors; 35 PRs typed `Bot` (dependabot), 8 by `openprojectci`. No deleted author, no NUL character, no truncated diff. The largest PR, 24173, has 504 files, all stored. 454 files came without a patch (binary or too large), as GitHub returns them.

## Verification evidence (19 Nov 2026)

```
$ ./mvnw verify
[INFO] Tests run: 248, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

$ java -jar backend/target/ei.jar   (log)
Migrating schema "public" to version "3 - raw record nul escaped"
Migrating schema "public" to version "4 - job run"
Migrating schema "public" to version "5 - github bot logins"
Successfully applied 3 migrations to schema "public", now at version v5

$ curl -s -i -X POST localhost:8080/api/jobs/github-import/runs -H 'Content-Type: application/json' \
    -d '{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14"}'
HTTP/1.1 202
Location: http://localhost:8080/api/jobs/runs/1

$ curl -s localhost:8080/api/jobs/runs/1
"status": "succeeded", "startedAt": "2026-11-19T09:08:41.847005Z", "finishedAt": "2026-11-19T09:15:03.770572Z",
"counts": {"pull_request.created": 215, "pull_request_comment.created": 367, "pull_request_diff.created": 215,
           "pull_requests_listed": 215, "review.created": 674, "review_comment.created": 735}
```

**1. Count matches GitHub** (design 10.1):

```
$ gh api -X GET search/issues -f q='repo:opf/openproject is:pr is:merged merged:2026-07-01..2026-07-14' --jq .total_count
215
select count(distinct external_id) from raw_record where record_kind = 'pull_request'
  and payload->>'mergedAt' >= '2026-07-01' and payload->>'mergedAt' < '2026-07-15';
215
PRs without a diff row: 0
```

**2. A second run adds no rows** (design 10.2):

```
rows before: 2206
run 2: succeeded, counts: pull_request.unchanged 215, pull_request_comment.unchanged 367,
       pull_request_diff.unchanged 215, pull_requests_listed 215, review.unchanged 674, review_comment.unchanged 735
rows after: 2206
select record_kind, external_id, version from raw_record where version > 1;
(0 rows)
```

**3. A changed PR is a new version, the old one kept** (design 10.3): `GitHubImportEndToEndTest.aChangedPullRequestIsANewVersionAndTheOldOneIsKept` and `PullRequestRecorderTest.anEditedBodyMakesANewVersionOfThePullRequestRowOnly`. The real re-run found no change, so there is no real pair to list.

**4. CI:** pending the pull request.
