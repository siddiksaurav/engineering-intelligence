# F02 GitHub connector: design

State: approved on 2026-11-19

## 1. Goal

After F02 the database holds an unchanged copy of every merged pull request of a GitHub repository in a range of merge dates: the PR itself, its files and per-file diff, its reviews, its inline review comments and its conversation comments. Later features build and judge evidence from these rows. F02 also builds the job mechanism every later batch step uses: a launcher that runs a job and records the run in `job_run`, and the HTTP endpoint that starts it. The GitHub import is the first job.

## 2. Scope

**In.**
- The job mechanism: `job_run` table, launcher, `POST /api/jobs/{name}/runs`, `GET /api/jobs/runs/{id}`, the localhost-only guard, marking runs left over by a crash.
- The job `github-import`: lists merged PRs by merge-date range or takes a list of PR numbers, fetches each PR with everything listed in 6.2, stores it in `raw_record`.
- Escaping of the NUL character in every `raw_record` payload (all connectors, not only GitHub).
- The setting `github.bot_logins`.
- The test setup no longer reads the developer's `.env`.
- The first run: 1 to 14 Jul 2026.

**Out.**
- Deciding what is a bot, a review comment that counts, or evidence of any kind: F04 and F05. F02 stores everything.
- Reading the stored GitHub rows for other features (list by merge date, by author, children of a PR): built by the first feature that reads them, F03 (see 11, decision 13).
- Pulling the PRs of long features whatever their date: F03 calls this job with a list of PR numbers.
- Commits, release tags, pipeline files and READMEs (spec 5, source 1): not needed for the PoC's evidence types.
- A screen for jobs, a scheduler, authentication: out of scope (master plan 4).
- Progress output, cost cap and run report for the full window: F09.

## 3. Rules from the spec and the PoC plan

- **Spec 5, source 1.** "Git host (GitHub or GitLab). What is pulled: commits, PRs, reviews and review comments, files changed, release tags, pipeline files, READMEs. Person: Git handle or commit email, looked up in the identity map. Work item: the OpenProject id in the branch name or commit message." The PoC pulls PRs, reviews, review comments and files only; the work item link is read by F03 from the PR body and branch name.
- **Spec 5, "What happens to each record".** "Every item keeps a link back to its source record, so a person can always open the original." Every row carries the GitHub URL of what it holds.
- **Master plan 2, Data.** "GitHub through the GraphQL API with a read-only token." "Window: Q3 2026 (1 Jul to 30 Sep 2026) plus one month on each side, so 1 Jun to 31 Oct 2026."
- **Master plan 2, Rules.** "`raw_record` is an unchanged copy of the source. It is never edited." "Bot accounts (dependabot, the CI account, the Copilot review bot) and an author's replies in their own PR never produce evidence items." F02 keeps the copy complete and stores bots too; the rule is applied by the builders (decision 5 below).
- **Master plan 2, Way of working.** "Jobs are started by a REST endpoint, and every run is recorded. There is no command-line runner and no scheduler."
- **F01 design 13.2.** "A piece of batch work is a job: a Spring service with a name and arguments. One launcher runs any job and records the run in a `job_run` table: job, arguments, who or what started it, start, end, outcome, counts. A job is started through an HTTP endpoint. During the build, before screens exist, the session starts jobs with `curl`. Later a button calls the same endpoint."
- **Decision 23.** `raw_record` has one row per version of a record. A fetched record is compared with the latest version's content hash; a different hash writes the next version.
- **Decision 24.** A missing setting is an error, never a default in code.
- **`poc-plan.md` 1.** "Reviews and inline review comments are available with file path and diff hunk. Many comments come from the Copilot review bot, and many are the author's own replies. Both must be separated from human reviewer comments." "PRs carry labels for AI usage. The label is ignored." F02 stores labels as GitHub returns them; ignoring them is the builders' rule.

## 4. What the real data looks like

Checked on 19 Nov 2026 with `gh api` (GraphQL and REST) against `opf/openproject`.

- **Counts.** GitHub search `repo:opf/openproject is:pr is:merged merged:2026-07-01..2026-07-14` gives 215. The window `merged:2026-06-01..2026-10-31` gives 2,377. In the two weeks: 35 PRs by `dependabot`, 8 by `openprojectci`, 172 by 24 people.
- **Bots.** GraphQL types `dependabot`, `copilot-pull-request-reviewer`, `github-actions` and `github-advanced-security` as `Bot`. `openprojectci` (the CI account; it opens "Merge release/17.6 into dev" and "Run npm audit fix") is typed `User`, so the type alone does not find it. In 100 PRs: 101 comments by `github-actions` (for example "The provided work package …" on PR 24204), 30 by Copilot, 8 by `github-advanced-security`. The REST search names dependabot `dependabot[bot]`; GraphQL names it `dependabot`.
- **Branches.** 79 of 100 PRs merge into `dev`, 21 into `release/17.6`. Some merge into another PR's branch (PR 24106 into `implementation/74970-backlogs-pragmatic-dnd`). GitHub counts all as merged.
- **Size.** Files per PR: median 3, 90th percentile 18, maximum 504 (PR 24173, CI merge) and 468 (PR 24172, a person). Lines changed: median 39, 90th percentile 759, maximum 13,475.
- **Diff.** The REST whole-PR diff (`Accept: application/vnd.github.diff`) refuses PR 24173: "the diff exceeded the maximum number of files (300)". The REST list of files (`GET /repos/{owner}/{repo}/pulls/{number}/files`, 100 per page, 3,000 files at most, documented) returns per file `filename`, `status`, `additions`, `deletions`, `changes`, `patch`, `sha`, `blob_url`, `raw_url`, `contents_url` (and `previous_filename` on a rename). GitHub leaves `patch` out for a file whose diff is too large (documented).
- **Reviews and comments.** Reviews per PR: median 1, maximum 13. Review threads per PR: median 0, 90th percentile 3, maximum 12. PR 24204 has 13 reviews, 12 threads, 3 conversation comments; 10 of its reviews are the author's own, empty-bodied, from replying in threads.
- **Fields** (PR 24204). PR: `databaseId`, `number`, `url`, `title`, `body`, `author { login, __typename, databaseId }`, `labels`, `baseRefName`, `headRefName`, `createdAt`, `mergedAt`, `mergedBy`, `isDraft`, `files { path, additions, deletions, changeType }`. Review: `databaseId`, `url`, `author`, `state`, `body`, `submittedAt`. Inline comment: `databaseId`, `url`, `author`, `body`, `path`, `diffHunk`, `replyTo { databaseId }`, `pullRequestReview { databaseId }`, `createdAt`. Conversation comment: `databaseId`, `url`, `author`, `body`, `createdAt`.
- **Cost.** GraphQL: 5,000 points an hour; 100 PRs with reviews and threads cost 23 points. REST: 5,000 requests an hour. One files request per PR (one per 100 files).

## 5. Behaviour

### 5.1 Starting a job

1. A client sends `POST /api/jobs/{name}/runs` with a JSON object of string arguments, for example `{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14"}`.
2. A request whose remote address is not a loopback address (`127.0.0.0/8`, `::1`) gets `403`. This guard covers every path under `/api/jobs`.
3. Unknown job name: `404` with the known names.
4. The launcher asks the job to check its arguments. A bad argument: `400` with the message, which names the argument and value. No run is recorded.
5. The launcher inserts a `job_run` row with status `running`. If a run of that job is already `running`, the database refuses the row and the client gets `409` with the id of the running run.
6. The client gets `202` with `{"id": …, "status": "running"}` and a `Location` header to `GET /api/jobs/runs/{id}`.
7. The job runs on a background thread. It adds to named counts as it goes; the launcher writes the counts to the row at least every 30 seconds and at the end.
8. At the end the row gets `finished_at`, the final counts and status `succeeded` or `failed` with the error message. An exception is never swallowed: it is logged with its stack trace and its message is stored.

`GET /api/jobs/runs/{id}` returns the row as JSON (`404` if unknown). It has the same localhost guard.

At start, the application sets every `running` row to `interrupted` with `finished_at` = now: a run cannot survive a restart.

### 5.2 The GitHub import job

Name: `github-import`. Arguments (all strings):

| Argument | Rule |
| --- | --- |
| `repository` | Required. `owner/name`, for example `opf/openproject` |
| `mergedFrom`, `mergedTo` | ISO dates, inclusive, in UTC as GitHub search reads them. `mergedTo` is not before `mergedFrom` |
| `pullRequestNumbers` | Comma-separated positive numbers, for example `24204,24201` |

Exactly one of the two forms (the range, or the numbers) is given. Anything else is a `400`.

Steps:

1. **List.** For a range: one GitHub search per day, `repo:{repository} is:pr is:merged merged:{day}`, read 100 at a time. Search returns at most 1,000 results per query; per day this is far above the real ~30, and a day that reports more than 1,000 fails the run with a message that names the day. For numbers: the list as given.
2. **Fetch**, for each PR, one at a time:
   - GraphQL: the PR fields, all files, all reviews, all review threads with all their comments, all conversation comments. Any connection with more than one page (100) is read to the end.
   - REST: the list of files with patches, all pages.
   - A PR number that is not a merged PR in that repository is counted as `not_merged` (or `not_found`) and stored nothing.
3. **Store** the records of one PR in one transaction (layout in 6.2). Each `store` returns `CREATED`, `UNCHANGED` or `NEW_VERSION`; the job counts each per record kind.
4. **Fail one PR, not the run.** A PR whose fetch or store fails after retries is counted as `failed`, logged with its number and reason, and the job goes on. At the end, if any PR failed, the run fails with "N of M pull requests failed: #a, #b, …" (first 20 numbers). Running the job again retries them and skips everything unchanged.
5. **Fail the whole run at once** when the failure is not about one PR, so nothing is missed silently and no time is spent retrying every PR:
   - a day's search still fails after retries, or GitHub answers it with `incomplete_results: true` after retries (a short list would break the "Done when" count). Nothing is fetched for any day after a listing failure; PRs of earlier days already stored stay stored;
   - GitHub answers `401`, or `403` without a rate-limit sign (bad, expired or under-privileged token). The message says the token was refused and never contains it.

Counts written to `job_run.counts`, for example:

```json
{"pull_requests_listed": 215, "pull_requests_failed": 0,
 "pull_request.created": 215, "pull_request.unchanged": 0, "pull_request.new_version": 0,
 "review.created": 391, "review_comment.created": 288, "...": 0}
```

### 5.3 Talking to GitHub

- The token is `GITHUB_TOKEN` from `.env`, read-only. It is sent as a bearer token and never logged, printed or put in a message. A run without a token fails at once with "GITHUB_TOKEN is not set".
- Calls are made one at a time (GitHub's secondary limits punish parallel calls).
- **Rate limit.** Every response's remaining budget is read (GraphQL `rateLimit`, REST `x-ratelimit-remaining` and `x-ratelimit-reset`). When the budget is nearly spent, the job waits until the reset. A `403` or `429` that names a rate limit, or carries `retry-after`, waits as told and retries.
- **Transient errors** (5xx, timeouts, connection reset): three retries with growing waits, then the PR fails (5.2 step 4).
- **Responses are validated** at the boundary: a GraphQL `errors` list, a missing PR, or a field of the wrong type is an error with a message that names the PR and the field.
- All JSON from GitHub is parsed with `CanonicalJson.parse(String)` (F01 notes), so numbers keep their exact form and the content hash is stable.

### 5.4 NUL characters in any payload

`jsonb` cannot hold U+0000. `RawRecordStore.store` replaces every U+0000 in any text (keys and values) with the six characters `\u0000` before hashing and storing, and sets the row's `nul_escaped` to true. The hash is taken over the escaped payload, so storing the same source again gives `UNCHANGED`. The original is restored by replacing `\u0000` with U+0000 in a row whose `nul_escaped` is true. This holds for every connector. `RawRecord.isNulEscaped()` exposes the column, like every other column. F02 builds no restore step: `getPayload()` returns the stored text, so a payload read back still hashes to its `content_hash` (F01 invariant), and no planned reader needs the NUL itself; a visible `\u0000` in a PR body is harmless to link parsing and to the model. If such a text also contained the literal six characters `\u0000`, the restore would turn those into NUL as well; this is accepted as too rare to handle (see 12).

## 6. Data

### 6.1 New migrations

**`job_run`**. Unlike the evidence tables, a row is updated while its run goes on (status, counts, end). It is an operational log, not evidence.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint, identity | |
| `job_name` | text | |
| `arguments` | jsonb | The arguments as given, an object of strings |
| `started_by` | text | `api` for the HTTP endpoint. A scheduler later writes `scheduler`; authentication later writes the user |
| `status` | text | `running`, `succeeded`, `failed`, `interrupted`. Check constraint |
| `started_at` | timestamptz | |
| `finished_at` | timestamptz, nullable | Null while running |
| `counts` | jsonb | Object of name to number. `{}` at start |
| `error_message` | text, nullable | Set when `failed` or `interrupted` |

Unique partial index on `job_name` where `status = 'running'`: one running run per job. Check: `finished_at` is null exactly when `status = 'running'`.

**`raw_record`**: new column `nul_escaped boolean not null default false`. Adding a column does not touch the trigger of V2; no row is updated.

**`setting`**: one row, `github.bot_logins`, valid from `2026-Q2` (the window starts 1 Jun 2026), value:

```json
["dependabot", "openprojectci", "copilot-pull-request-reviewer", "github-actions", "github-advanced-security"]
```

Description: "GitHub logins that are bots. An account GitHub types as Bot is also a bot. Read by F04 and F05; F02 stores bot records like any other." F02 itself does not read it.

### 6.2 The GitHub rows in `raw_record`

Data source `github`. The external id of a PR includes the repository, because the data source is all of github.com and PR numbers repeat across repositories. Reviews and comments use GitHub's `databaseId`, which is unique across GitHub.

| `record_kind` | `external_id` | `url` | Payload |
| --- | --- | --- | --- |
| `pull_request` | `opf/openproject#24204` | PR URL | The GraphQL PR node: `databaseId`, `number`, `url`, `title`, `body`, `author`, `labels` (names), `baseRefName`, `headRefName`, `createdAt`, `mergedAt`, `mergedBy`, `isDraft`, `repository { nameWithOwner }`, and `files`: all files as one list of `{path, additions, deletions, changeType}` |
| `pull_request_diff` | `opf/openproject#24204` | PR URL + `/files` | `{"files": [...], "truncated": false}`. `files` is the REST list of files, all pages, each object as GitHub returned it. `truncated` is true when the PR has more than 3,000 files |
| `review` | `4683447746` | review URL | The GraphQL review node: `databaseId`, `url`, `author`, `state`, `body`, `submittedAt`, `pullRequest { number, repository { nameWithOwner } }` |
| `review_comment` | `3569514765` | comment URL | The GraphQL inline comment node: `databaseId`, `url`, `author`, `body`, `path`, `diffHunk`, `replyTo { databaseId }`, `pullRequestReview { databaseId }`, `createdAt`, `pullRequest { number, repository { nameWithOwner } }` |
| `pull_request_comment` | `4956278455` | comment URL | The GraphQL conversation comment node: `databaseId`, `url`, `author`, `body`, `createdAt`, `pullRequest { number, repository { nameWithOwner } }` |

`author` is `{login, __typename, databaseId}` everywhere. `databaseId` is kept because a login can be renamed and F04 needs a stable id. A deleted GitHub account comes back as `author: null` and is stored so.

**What is never requested,** so a re-fetch of an unchanged record gives the same hash: totals of child connections (`totalCount`), `pageInfo`, `updatedAt`, reactions, `isResolved` and other review-thread state, `viewer*` fields. The query is the definition of what is stored; changing it changes every hash and makes a new version of every record on the next run, so it changes only with a decision.

**Versions.** An edited PR title or body, a changed label, an edited comment: the next run writes a new version of that one row (decision 23). A new comment adds one row. A comment deleted on GitHub stays in `raw_record` with no mark (12).

## 7. Interfaces

**Uses** (from F01, exact):

```java
package com.dsinnovators.ei.source;
public interface RawRecordStore {
    StoreResult store(String dataSourceKey, String recordKind, String externalId, String url, JsonNode payload);
    Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId);
    List<RawRecord> findVersions(String dataSourceKey, String recordKind, String externalId); // oldest first
    List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind);
}
public record StoreResult(Outcome outcome, RawRecord record) {
    public enum Outcome { CREATED, UNCHANGED, NEW_VERSION }
}

package com.dsinnovators.ei.common;
public final class CanonicalJson { public static JsonNode parse(String json); ... }

package com.dsinnovators.ei.job;
class JobRegistry { JobRegistry(List<Job> jobs); Optional<Job> find(String name); List<String> names(); }
```

`JsonNode` is `tools.jackson.databind.JsonNode` (Jackson 3).

**Changes** (F01's `Job` is unused so far, so it can change; decision 39):

```java
package com.dsinnovators.ei.job;

/** A named piece of batch work, started through the launcher. A job is a Spring bean. */
public interface Job {
    String name();

    /** Checks the arguments before a run is recorded.
     *  @throws IllegalArgumentException with a message that names the argument and its value */
    void checkArguments(JobArguments arguments);

    /** Runs the job. Returning means success; an exception means failure. */
    void run(JobArguments arguments, JobCounts counts) throws Exception;
}

/** Named counts a job adds to while it runs; the launcher stores them on the run. Thread-safe. */
public interface JobCounts {
    void add(String name, long amount);
}
```

`JobArguments` stays as in F01 (`Optional<String> get(String name)`, `String require(String name)`); F02 adds its implementation.

**Gives.**
- `POST /api/jobs/{name}/runs`, `GET /api/jobs/runs/{id}`, as in 5.1. Response body of both: `{"id", "jobName", "arguments", "startedBy", "status", "startedAt", "finishedAt", "counts", "errorMessage"}`.
- The job `github-import` (5.2). F03 starts it with `pullRequestNumbers`.
- The `raw_record` layout of 6.2.
- `raw_record.nul_escaped`, set by `RawRecordStore.store` for every connector.
- The setting `github.bot_logins`.
- Table `job_run` (6.1).
- `DatabaseTest` also empties `job_run`.

## 8. Cases that must work

| Case | What happens |
| --- | --- |
| The same range is run twice | Second run: every record `UNCHANGED`, no new rows |
| A PR's body was edited between runs | `pull_request` gets version 2; its other rows are `UNCHANGED` |
| A new comment arrived after the first run | One new `review_comment` or `pull_request_comment` row; the PR row is `UNCHANGED` |
| A PR with more than 100 files, reviews, threads or comments in a thread | All pages are read |
| A PR with more than 300 files | Patches come from the per-file list, not the whole-PR diff |
| A PR with more than 3,000 files | Stored with the first 3,000 and `truncated: true`; counted as `pull_request_diff.truncated` |
| A file whose patch GitHub leaves out | Stored without `patch`, as GitHub returned it |
| A text with U+0000 | Escaped, `nul_escaped` true (5.4) |
| An author whose account was deleted | `author: null` stored |
| A PR number that is not merged, or does not exist | Counted `not_merged` or `not_found`; nothing stored; the run does not fail for it |
| GitHub rate limit reached | The job waits until the reset and goes on |
| GitHub fails for one PR after retries | That PR is counted `failed`; the others are stored; the run ends `failed` and names it |
| `GITHUB_TOKEN` missing | The run fails at once with "GITHUB_TOKEN is not set" |
| A second start while the job runs | `409` with the running run's id |
| The application stops during a run | At the next start the row becomes `interrupted`; running the job again completes it |
| A call from another machine | `403` |
| Both a range and numbers, or neither; a bad date; `mergedTo` before `mergedFrom` | `400`, nothing recorded |
| A day with more than 1,000 merged PRs | The run fails and names the day |

## 9. How it is run

Put `GITHUB_TOKEN` in `.env` (read-only; a fine-grained token with public repository read access is enough). Start the backend, then:

```
curl -s -X POST localhost:8080/api/jobs/github-import/runs \
  -H 'Content-Type: application/json' \
  -d '{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14"}'
curl -s localhost:8080/api/jobs/runs/1
```

The backend log shows one line per day listed and one per PR stored, with its outcome.

## 10. How it is checked

From the master plan's "Done when", made exact. Evidence goes into `notes.md`.

1. **Count matches GitHub.** After the run for 1 to 14 Jul 2026: `select count(distinct external_id) from raw_record where record_kind = 'pull_request' and payload->>'mergedAt' >= '2026-07-01' and payload->>'mergedAt' < '2026-07-15'` equals the `total_count` of `gh api -X GET search/issues -f q="repo:opf/openproject is:pr is:merged merged:2026-07-01..2026-07-14"`, taken the same day. Every `pull_request` has a `pull_request_diff`.
2. **A second run adds no rows.** `select count(*) from raw_record` before and after the second run are equal; the run's counts show only `unchanged`.
3. **A changed PR is a new version, the old one kept.** Covered by an automated test with a recorded response whose body differs (two versions, version 1 unchanged). On real data, if a re-run shows any `new_version`, both versions of that record are listed in `notes.md`.
4. `./mvnw verify` is green; CI is green on the pull request.

## 11. Decisions made in this deep dive

| # | Question | Answer | Reason | By |
| --- | --- | --- | --- | --- |
| 1 | One row per PR, or per PR and its parts | One row per PR, per review, per inline comment, per conversation comment, plus one diff row per PR (6.2) | Each row is something a later feature points at (a Review comment written item links to its own comment row); a late comment or an edited title never copies the diff | User |
| 2 | How the diff is fetched and large diffs stored | REST list of files with per-file patches, all pages, in its own row. More than 3,000 files: `truncated` | The whole-PR diff refuses more than 300 files (checked on PR 24173) | Session |
| 3 | Bot PRs and comments stored and marked, or skipped | Stored like any other record. Not marked in `raw_record` | `raw_record` stays a complete copy; the "Done when" count matches GitHub search; changing the bot list never needs a re-fetch | User |
| 4 | Where the bot list is kept | Setting `github.bot_logins`, plus the rule "GitHub type Bot is a bot" | A list that may change is data, like the done statuses in `setting` (master plan 2), not a constant in code. `openprojectci` is a `User` on GitHub, so the type alone misses it | Session |
| 5 | Conversation comments | Stored, as `pull_request_comment` | People give review feedback there too; F05 decides whether it counts. About one request per PR | Session |
| 6 | Rate limit and resume after a failure | Wait for the reset; three retries on transient errors; one PR per transaction; a re-run is the resume, because unchanged records are skipped by hash | Works for any range length without a checkpoint table | Session |
| 7 | Versioning of a changed record | Decision 23 as built in F01; the fields that change without a real change are never requested (6.2) | Keeps false versions out | Session |
| 8 | Listing a range | One search per day | Search returns at most 1,000 results per query; the window has 2,377 | Session |
| 9 | NUL characters | Escaped as `\u0000` by `RawRecordStore.store` for all connectors, row marked `nul_escaped` | `jsonb` cannot store U+0000; no data is lost and the change is visible | User |
| 10 | A second run of a job while one is running | Refused with `409`, enforced by a unique partial index. Different jobs may run at once. A crash leaves `interrupted` | Two imports of one source at once would race on versions | Session |
| 11 | What a run records as counts | Named counts per record kind and outcome, plus listed, failed, not found (5.2) | Enough to check "Done when" from the run alone | Session |
| 12 | Who may call the endpoint without authentication | Only callers on the same machine (loopback address) | User's choice; the PoC runs on one machine | User |
| 13 | Reading the GitHub rows for later features (master plan "Gives") | Not built in F02. F03, the first reader, builds the reader against the layout of 6.2 | Built against its first real use, and no code waits unused | Session |
| 14 | External id of a PR | `owner/name#number` | The data source is all of github.com; numbers repeat across repositories | Session |
| 15 | Which branches | Every merged PR, whatever its base branch | Release-branch and stacked PRs are real work; GitHub search counts them | Session |
| 16 | Tests and `.env` | Tests do not load `.env` and never call GitHub; they use recorded responses | Closes the open point of F01 before a token is in `.env` | Session |
| 17 | First run | 1 to 14 Jul 2026, `opf/openproject` | The first two weeks of the quarter; 215 PRs | Session |
| 18 | Where the connector's and launcher's operating numbers live (rate-limit reserve, retries, count flush interval) | Spring configuration properties under `ei.github` and `ei.jobs` in `application.yml`, validated at start. GitHub's own limits (100 per page, 1,000 per search, 3,000 files) are named constants with a comment that cites GitHub's documentation. `setting` stays for the rules of the evaluation (thresholds, weights, statuses, lists such as the bot logins), valid by quarter | `setting` is versioned by quarter because it changes a score; a retry count changes no result. The CLAUDE.md rule "thresholds and weights are rows in `setting`" is about the spec's numbers (master plan 2: "No number from the spec is hard-coded") | Session |
| 19 | Failures that are not about one PR (listing, refused token) | The run fails at once (5.2 step 5) | An incomplete list would miss PRs silently; a refused token would otherwise retry every PR | Session |
| 20 | Who restores an escaped NUL | Nobody in F02; the flag and the rule are enough (5.4) | No planned reader needs the NUL; restoring on read would break the hash check of F01 | Session |

**Left to the builder.** Unknown argument keys and non-string argument values get `400`. `pageInfo` is requested to page but not stored; merged connections (labels, files) are stored as plain arrays. External ids use the `nameWithOwner` GitHub returns, not the argument's spelling. An IPv4-mapped loopback address (`::ffff:127.0.0.1`) counts as local. The HTTP client library (Spring's `RestClient`, or Spring for GraphQL's client over it; check what Spring Boot 4.1.2 offers at build time). How recorded responses are served in tests. The executor for background runs. Class names inside `job` and `source.github` that section 7 does not fix. The wait times between retries.

## 12. Still open

- **A comment deleted on GitHub** stays in `raw_record` unmarked. Whether a later feature needs to know (for example to drop a deleted review comment from evidence) is decided by F05.
- **The literal text `\u0000` next to a real NUL** in the same record would not restore exactly (5.4). Not handled; revisit only if it is ever seen.
- **A proxy in front of the backend** would make every caller look local. There is no proxy in the PoC; authentication replaces the guard when it is built.
- **Commits, tags, pipeline files** (spec 5, source 1) are not pulled. A later feature that needs them adds record kinds to this job.
