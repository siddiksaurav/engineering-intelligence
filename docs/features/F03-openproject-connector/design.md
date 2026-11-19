# F03 OpenProject connector: design

State: approved on 2026-11-19, through the question brief

## Goal

After F03 the database holds an unchanged copy of the OpenProject work items behind the stored pull requests: each referenced work item, its ancestors, the whole tree under every Feature reached, the activities (status history with who and when) and the list of GitHub pull requests OpenProject shows on each item, plus their projects. Each stored pull request has a resolved link to its work items. For every Feature that reached a done status in the quarter, every merged pull request linked to it or to an item under it is in `raw_record` too, whatever its merge date. Later features (F04 identity, F05 builders) read all of this without calling OpenProject.

## In scope / Out of scope

**In.**
- The job `openproject-import` (Main flows): reads stored pull requests, finds their work item references, resolves them on the server, fetches the work items and what hangs on them, stores them in `raw_record`, fills `project`, and writes the link table `pull_request_work_item`.
- The completion step: for each Feature done in the quarter, imports the missing merged pull requests of the run's repository through F02's import code, and links them.
- The setting `openproject.done_statuses`.
- The readers F03 itself uses: stored pull requests by merge date and by number (decision 46).
- The readers it gives: a work item by id, the links of a pull request, the nearest ancestor of type Feature.
- The first run: the pull requests merged 1 to 14 Jul 2026, quarter 2026-Q3.

**Out.**
- Deciding who a person is (the "Developers" field, the status history users): F04. F03 stores the data.
- Deciding which pull request belongs to which Feature work item, what a Bug is in a group, which quarter an item counts in: F05. F03 stores every link it finds, not a chosen one.
- Readers of stored pull requests by author, and of a pull request's reviews and comments: built by their first users, F04 and F05 (decision 46, see Decisions).
- Relations between work items ("relates to", "blocks"): not in the master plan's list for F03. F07 adds them if its prompt uses them.
- Work items found by update date, without a pull request pointing to them (see Decisions).
- The full window: F09. Progress output, cost cap, run report: F09.
- Pull requests in other repositories that OpenProject lists on a work item.

## Data and tables

### Rules from the spec and the PoC plan

- **Spec 5, source 2.** "OpenProject work items. What is pulled: features, tasks, Design tasks, bugs, status history, links, reopens, sprint moves with their reason. Person: assignee and creator. Work item: the item itself." The PoC does not use the assignee to find the person (`poc-plan.md` 1, below). Sprint moves do not exist in this data.
- **Spec 5, source 1.** "Work item: the OpenProject id in the branch name or commit message." The real data carries it in the PR body instead (below).
- **Spec 5, "What happens to each record".** "Every item keeps a link back to its source record, so a person can always open the original." Every row carries the OpenProject URL of what it holds.
- **`poc-plan.md` 1, OpenProject** (checked 16 Nov 2026): "Hierarchy exists: Epic > Feature > Implementation / Task / Bug. About half of items have a parent. Full status history per item, with who and when. Statuses include 'review failed' and 'test failed', which can stand in for reopens. Each work item lists its GitHub PRs, so the link works from both sides. Custom fields 'Developers' and 'Module' exist. Work items have two ids: a number (81224) and a project key (COMMS-1078). The key changes when an item moves project (SE-694 and COMMS-1078 are the same item)."
- **`poc-plan.md` 1, "Missing or weak".** "Assignee is cleared at each hand-over; only 21% of items have one. Use the PR author, the 'Developers' field and the status history."
- **`poc-plan.md` 3.1 item 1.** "Collect PRs and work items (with history, parent, linked PRs) for one quarter, Q3 2026 (1 Jul to 30 Sep), plus one month on each side. In addition, for every feature that reached a done status in the quarter, all its linked PRs are pulled whatever their date, so long features are complete."
- **`poc-plan.md` 3.7 step 3.** "OpenProject connector: work items referenced by those PRs, their ancestors, children, activities; and for each feature done in the window, all its linked PRs whatever their date." The PoC follows 3.1: the quarter (decision 68).
- **Master plan 2.** "OpenProject through API v3 without login." "Thresholds, weights and done statuses are rows in `setting` (spec 14)." "`raw_record` is an unchanged copy of the source. It is never edited."
- **Decision 23.** One `raw_record` row per version; a different content hash writes the next version.
- **Decision 41 and F02 design 6.2.** "The query is the definition of what is stored"; fields that change without a real change are never requested.

### What the real data looks like

Checked on 19 Nov 2026 against `https://community.openproject.org/api/v3` without login, and against the 215 pull requests of 1 to 14 Jul 2026 stored by F02.

- **References in pull requests.** Of 172 human pull requests (bots: GitHub type `Bot` or a login in `github.bot_logins`), 146 have an OpenProject work item URL in the body. Forms seen: `https://community.openproject.org/wp/AGILE-218`, `.../work_packages/OP-19204`, `.../projects/COMMS/work_packages/COMMS-806`, `.../projects/SJF/work_packages/COMMS-1078` (the project in the path can differ from the key), `.../projects/openproject/work_packages/56343` and `.../wp/71971` (numbers). 130 pull requests carry one reference, 11 carry two, 5 carry 4 to 9. 163 distinct references.
- **Titles and branches.** 85 titles carry a key such as `[OP-19345]`; only one (PR 24086, a backport whose body has no URL) adds a work item the body does not have. Branch names add the same one PR (`bug/op-19345-…`) and also give a false match (`housekeeping/npm-11-allow-scripts`). No body uses `OP#1234`.
- **Resolution.** `GET /api/v3/work_packages/{ref}` accepts the number, the current key and an old key: `81224`, `COMMS-1078` and `SE-694` all return item 81224, `displayId` `COMMS-1078`. Of the 163 references, 24 are old keys of moved items (for example `FND-101` is now `AUTOWORK-69`, `OP-19204` is now `RESMA-17`, `STC-730` is now `COMMS-730`). 12 return `404`: items in projects that are not public (`DEV`, `DEVOPS`, `INTERNAL`, `SC`).
- **Share resolved.** 132 of 172 human pull requests resolve to at least one visible work item (77%). With the title rule, 133 (77%). The master plan expects about 78%.
- **Collection fetch.** `GET /api/v3/work_packages?filters=[{"id":{"operator":"=","values":[…]}}]&pageSize=100` returns the items with the same fields and links as a single fetch, but without `_embedded` (no copy of the project, status, type, author, which belong to other records). It accepts numbers and current keys. One unknown or non-visible value fails the whole call with `400` "Id filter has invalid values". An old key in the filter returns the item under its new `displayId`, so the caller cannot tell which value it answered.
- **Types** of the 151 visible referenced items: Bug 60, Implementation 30, Feature 28, Code maintenance 17, Documentation 13, Open Point, Task, Milestone 1 each. Status: closed 143, new 4, tested 2, merged 1, needs review 1. 58 have a parent; 93 have none. The server also has a type "Bug App" (id 158); none of the referenced items uses it.
- **Hierarchy.** `_links.parent` and `_links.ancestors` (top first, the parent last) carry href, title and `displayId`. `_links.children` lists direct children. Filter `ancestor = {id}` returns the whole tree under an item (Epic 69622: 22 children, 42 in the tree); `parent = {id}` returns one level.
- **Custom fields** (schema `work_packages/schemas/1395-6`): `customField270` "Developers" (list of users), `customField429` "Module" (list of hierarchy items). Both sit in `_links`. Others exist (Product manager, Feature Lead, Designer); all are kept as returned.
- **Activities.** `GET /api/v3/work_packages/81224/activities` returns all 48 in one answer (no paging seen). Each has `id`, `version`, `createdAt`, `updatedAt`, `comment`, `internal`, `details` (texts such as "Status changed from in review to review approved", "Developers set to Kabiru Mwenja", "Assignee deleted (Alexander Coles)") and `_links.user`. The first activity says "Status set to new". Some details read "Parent set to a non-visible work package".
- **Status path of a Feature** (14 Features looked at): `in development > needs review > (review failed >) review approved > merged > in test > tested > closed`. `closed` is set in release batches (24 Jul, 5 Aug, 26 Aug 2026); some Features stay at `tested` (AUTOWORK-70, AUTOWORK-71). The server's statuses marked closed: deployed, tested, resolved, released, paid, decided, no service provided, payment received, offer rejected, rejected, closed. Many of these belong to sales and administration work on the same server.
- **Linked pull requests.** `GET /api/v3/work_packages/81224/github_pull_requests` lists 8 pull requests with `id`, `number`, `repository`, `htmlUrl`, `title`, `state` (`open`, `closed`, `deployed`), `merged`, `mergedAt`, `draft`, `labels`, `createdAt`, plus `commentsCount`, `reviewCommentsCount`, `additionsCount`, `deletionsCount`, `changedFilesCount`, `updatedAt`, `githubUpdatedAt` and an embedded list of CI check runs. 184 KB for 8 pull requests, most of it check runs. The list includes pull requests that name an old key of the item (`[SE-694] …`).
- **Speed.** About 1 s per call (0.9 s for one item, 1.1 s activities, 1.3 s pull requests, 3.7 s for 42 items in one collection call). No rate-limit headers.
- **Two fetches** of item 81224 a minute apart are identical.

### Rows in `raw_record`

Data source `openproject` (exists since F01, base URL `https://community.openproject.org`). The external id of a work package is its number: it never changes, while the key does.

| `record_kind` | `external_id` | `url` | Payload |
| --- | --- | --- | --- |
| `work_package` | `81224` | `https://community.openproject.org/work_packages/81224` | The element of the collection answer, as returned (no `_embedded`) |
| `work_package_activity` | `1756926` (activity id) | `https://community.openproject.org/work_packages/81224/activity` | The activity element, as returned |
| `work_package_pull_requests` | `81224` | `https://community.openproject.org/work_packages/81224/tabs/github` | A reduced copy (decision 69): `{"reduced": true, "pullRequests": [...]}`, each entry with `id`, `number`, `repository`, `htmlUrl`, `title`, `state`, `draft`, `merged`, `mergedAt`, `createdAt` and `githubUser` (the login), in the order OpenProject returned them. Counters, update times and CI check runs are left out |
| `project` | `1395` | `https://community.openproject.org/projects/{identifier}` | `GET /api/v3/projects/{id}`, as returned |

### New table `pull_request_work_item`

The resolved links between a stored pull request and work items. Derived data, not a source copy: the rows of one pull request are replaced when the job runs for it again.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint, identity | |
| `pull_request_external_id` | text | `opf/openproject#24204` (the F02 external id) |
| `work_package_id` | bigint, nullable | The OpenProject number. Null when not visible |
| `reference` | text | As written: `FND-101`, `81224`, or the number for an OpenProject-side link |
| `found_in` | text | `pull_request_body`, `pull_request_title`, `openproject`. Check constraint |
| `state` | text | `resolved` or `not_visible`. Check constraint; `work_package_id` is null exactly when `not_visible` |
| `linked_at` | timestamptz | When this run wrote it |

Unique on (`pull_request_external_id`, `found_in`, `reference`). Index on `work_package_id`.

### `project`

One row per OpenProject project seen on a stored work package, from its `project` record: `external_id` the project id, `identifier`, `name`, `url`. A changed name or identifier updates the row; `project` is not a source copy.

### New setting

`openproject.done_statuses`, valid from `2026-Q2`, a list of status names (decision 67):

```json
["tested", "deployed", "resolved", "released", "closed"]
```

`rejected`, `no service provided` and the sales and invoice statuses are not done, although OpenProject marks them closed. Description: "OpenProject statuses that mean a work item is done. A Feature that changes into one of them within a quarter is done in that quarter. Read by F03 and F05."

## Main flows

### Starting the job

`POST /api/jobs/openproject-import/runs` (F02's endpoint, local callers only). Arguments, all text:

| Argument | Rule |
| --- | --- |
| `repository` | Required. `owner/name`; the stored pull requests of this repository are read |
| `mergedFrom`, `mergedTo` | ISO dates, inclusive: the stored pull requests merged in this range (UTC) |
| `pullRequestNumbers` | Comma-separated positive numbers of stored pull requests |
| `quarter` | Required. `2026-Q3`: the quarter of the completion step |

Exactly one of the range or the numbers. A number that is not stored is a `400` naming it (run `github-import` first). Anything else wrong is a `400` naming the argument.

```
curl -s -X POST localhost:8080/api/jobs/openproject-import/runs \
  -H 'Content-Type: application/json' \
  -d '{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14","quarter":"2026-Q3"}'
```

### Steps

1. **Read the pull requests** from `raw_record` (latest versions, record kind `pull_request`), with the new reader of stored pull requests.
2. **Find references** in each pull request:
   - the body: every URL on `community.openproject.org` of the form `/wp/{ref}`, `/work_packages/{ref}` or `/projects/{any}/work_packages/{ref}`, where `{ref}` is a key (`[A-Za-z][A-Za-z0-9]*-[0-9]+`) or a number; the base host is taken from the `openproject` data source;
   - the title: a key in square brackets, `[OP-19345]`.
   Keys are compared in upper case. Bot pull requests are read like any other; F05 drops them.
3. **Resolve** each distinct reference to a work package number:
   - numbers and keys in collection calls of up to 100 values (`filters=id`); a reference whose `displayId` or number is in the answer is resolved;
   - a reference not matched that way (an old key) is fetched alone with `GET /work_packages/{ref}`; its number is taken from the answer;
   - a collection call answered `400` is repeated one reference at a time;
   - a single fetch answered `404` gives `not_visible`.
4. **Collect the work packages to store:**
   - every resolved work package;
   - its ancestors (from `_links.ancestors`);
   - its direct children (from `_links.children`);
   - for every work package of type Feature among these (the type title `Feature`), the whole tree under it (filter `ancestor`, all pages).
   A parent the API does not show ends the walk up; this is not an error.
5. **Fetch and store** the work packages by number in collection calls of up to 100, and for each one its activities and its list of linked pull requests. Then the project of each, once per project. One work package with its activities and pull request list is stored in one transaction. Each store returns `CREATED`, `UNCHANGED` or `NEW_VERSION` (F02); the job counts them per record kind.
6. **Write the links** of each pull request: the body and title references (step 2, with step 3's result), plus an `openproject` link for every stored work package whose pull request list names this pull request. The pull request's earlier rows are replaced in the same transaction.
7. **Completion.** For each stored Feature that changed into a done status within `quarter` (below), take the merged pull requests of `repository` in the pull request lists of the Feature and of every work package under it. Those not yet in `raw_record` are imported through F02's import code (the same fetch and store as `github-import`), then go through steps 2 to 6. Repeat until a round finds no new pull request.

**Done in the quarter.** An activity of the Feature whose `createdAt` lies in the quarter and whose `details` holds a text "Status changed from … to {s}" or "Status set to {s}", where `{s}` is exactly a name in `openproject.done_statuses` for that quarter. The match is on the end of the text against the known names, so a status name that contains " to " is read correctly.

**Counts** in `job_run.counts`, for example: `pull_requests_read`, `references_found`, `references_resolved`, `references_not_visible`, `work_package.created`, `work_package.unchanged`, `work_package_activity.created`, `work_package_pull_requests.unchanged`, `project.created`, `pull_requests_linked`, `features_done_in_quarter`, `pull_requests_completed`, and F02's counts for the completed pull requests.

**Failures.** As in F02: a work package that fails after retries is counted `work_packages_failed`, the run goes on and fails at the end naming the first 20. A failed collection call that is not a `400` fails the run at once (an incomplete list would miss items silently). A failed GitHub import of a completion pull request is counted and named like a failed work package.

### Talking to OpenProject

- No login. Calls are made one at a time, with a pause between calls (configuration property, 200 ms to start) to be polite to a public server.
- Transient errors (5xx, timeouts, connection reset) and `429` are retried three times with growing waits (`Retry-After` is honoured); then the call fails.
- The `User-Agent` names the application generically (`engineering-intelligence-poc`), with no person or address.
- Responses are validated at the boundary: a missing `id`, `_links`, `_type` of the wrong kind, or an `_embedded.elements` that is not a list is an error naming the call and the field. All JSON is parsed with `CanonicalJson.parse(String)` (F01 notes).
- Operating numbers (base URL, page size, pause, retries, timeout) are Spring configuration properties under `ei.openproject` (decision 45).

**Uses** (from earlier features):

```java
package com.dsinnovators.ei.source;
public interface RawRecordStore {
    StoreResult store(String dataSourceKey, String recordKind, String externalId, String url, JsonNode payload);
    Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId);
    List<RawRecord> findVersions(String dataSourceKey, String recordKind, String externalId);
    List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind);
}
public record StoreResult(Outcome outcome, RawRecord record) { enum Outcome { CREATED, UNCHANGED, NEW_VERSION } }
// RawRecord: getId(), getRecordKind(), getExternalId(), getVersion(), getUrl(), getContentHash(), JsonNode getPayload(), getFetchedAt(), isNulEscaped()

package com.dsinnovators.ei.job;
public interface Job { String name(); void checkArguments(JobArguments a); void run(JobArguments a, JobCounts c) throws Exception; }
public interface JobArguments { Optional<String> get(String n); String require(String n); Set<String> names(); Map<String, String> asMap(); }
public interface JobCounts { void add(String name, long amount); }

package com.dsinnovators.ei.setting;
public interface SettingService { List<String> getTextList(String key, Quarter quarter); /* … */ }

package com.dsinnovators.ei.common;
public final class CanonicalJson { public static JsonNode parse(String json); }
public record Quarter /* parse("2026-Q3"), first and last day */;
```

`github-import` (F02): `GitHubImportJob` fetches with `PullRequestFetcher.fetch(repository, number)` (returns `PullRequestFetch.Found`, `NotFound`, `NotMerged`) and stores with `PullRequestRecorder.store(FetchedPullRequest)`. Both are package-private in `source.github`. F03 moves the per-number import out of `GitHubImportJob` into one public service in `source.github` that both jobs call, so the logic lives once. The F02 row layout (F02 design 6.2): a `pull_request` row has external id `opf/openproject#24204` and payload fields `number`, `title`, `body`, `author { login, __typename, databaseId }`, `headRefName`, `mergedAt`, `repository { nameWithOwner }`.

**Gives** (to later features), in package `source.openproject` unless named otherwise:
- The job `openproject-import` (above).
- `raw_record` rows `work_package`, `work_package_activity`, `work_package_pull_requests`, `project` (table above); `project` rows.
- Table `pull_request_work_item`.
- Setting `openproject.done_statuses`.
- `source.github`: a reader of stored pull requests: by repository and merge-date range, and by repository and number, returning the latest `pull_request` record.
- A reader of stored work packages: a work package by number; the links of a pull request (`pull_request_work_item` rows); the nearest work package of type Feature at or above a work package (itself, else the nearest ancestor, else empty); the status changes of a work package read from its activities (status name, time, user), used by the done check and by F05.

Exact class and method names are fixed in `plan.md`.

## Edge cases

| Case | What happens |
| --- | --- |
| The same run twice | Every record `UNCHANGED`, no new rows; links rewritten with the same content |
| A work package edited between runs | Its `work_package` row gets the next version; new activities add rows |
| A key that moved project (`SE-694`) | Resolved by a single fetch to 81224; the link keeps `SE-694` as written |
| A reference to an item in a non-public project | Link row with `state = not_visible`, no work package stored; counted |
| A collection call that fails `400` for one bad value | Repeated one value at a time; the bad one is `not_visible` |
| A pull request with several references | One link row per reference; F05 decides which counts |
| A pull request with no reference | No link row; F05 and F10 count it as unlinked |
| A parent the API hides | The walk up ends there; the item's own `_links.parent` keeps what OpenProject shows |
| A Feature with more than 100 items under it | All pages of the `ancestor` filter are read |
| A work package referenced by several pull requests, or reached twice in one run | Fetched and stored once per run |
| A completion pull request that is not merged, or in another repository | Not imported |
| A completion pull request that brings a new reference | Resolved and stored in the next round; rounds stop when no new pull request is found |
| A text with U+0000 | Escaped by `RawRecordStore.store` (decision 43) |
| A number in `pullRequestNumbers` that is not stored | `400` before the run, naming it |
| OpenProject unreachable or failing after retries | A work package call: that item fails, the run goes on and fails at the end. A listing call: the run fails at once |
| `github-import` runs at the same moment | Not prevented (different job names). Two stores of the same new version would collide on the unique constraint and that pull request would fail; a re-run completes it. Do not run both at once |

## How it will be tested

Unit and database tests with recorded OpenProject answers served by a mock server (as `GitHubApiFixture` does for GitHub); no test calls OpenProject or GitHub. Recorded answers: work package 81224 (collection element, activities, pull request list), its child 83452, an old-key single fetch, a `400` collection answer, a `404`.

The master plan's "Done when", made exact. Evidence goes into `notes.md`.

1. **About 78% of human-merged pull requests resolve.** After `openproject-import` for 1 to 14 Jul 2026 (quarter 2026-Q3): the share of human pull requests of that range (author type not `Bot`, login not in `github.bot_logins`) with at least one `pull_request_work_item` row in state `resolved`. Expected about 77% (133 of 172, measured 19 Nov 2026). Completion pull requests are counted separately and do not enter this share.
2. **Both forms of id and an old key resolve to the same item.** An automated test resolves `81224`, `COMMS-1078` and `SE-694` from recorded answers to 81224. On real data: `github-import` with `pullRequestNumbers` `26057,26061` (bodies or titles naming `SE-694` and `COMMS-1078`), then `openproject-import` with the same numbers; both link to work package 81224. Plus one pull request of the range whose reference is a number (`56343` or `71971`).
3. **A second run adds no rows.** `select count(*) from raw_record` and `select count(*) from pull_request_work_item` before and after a second run of the same arguments are equal, and the run's counts show only `unchanged` for OpenProject records. A real change on the server between the runs shows as `new_version`; each such record is listed in `notes.md`.
4. **The completion step pulls the pull requests of done Features.** The run lists the Features done in 2026-Q3 and the pull requests it imported for them; for two of them the session lists the pull requests with links, and the user checks them against the Feature's GitHub tab on OpenProject.
5. `./mvnw verify` green; CI green on the pull request.

No architecture test is disabled for F03.

## Decisions

Every point of this design is closed. Numbers are those of `docs/decisions.md`.

| # | Decision | By |
| --- | --- | --- |
| 56 | Work items are found only through pull requests: referenced items, their ancestors, their direct children, the whole tree under each Feature reached. None by update date | Session |
| 57 | Links from OpenProject URLs in the PR body and a `[KEY-n]` in the title; not branch names | Session |
| 58 | References resolved by the server: collection calls of 100, single fetch for unmatched (moved) keys, one at a time after `400`, `404` is not visible. External id of a work package is its number | Session |
| 59 | A work package is stored as the collection element; one row per activity and per project; `project` filled from project rows | Session |
| 60 | Derived link table `pull_request_work_item`, rewritten per pull request; every link kept | Session |
| 61 | Completion inside the same run through one import service shared with `github-import`; rounds until nothing new; merged pull requests of the run's repository only | Session |
| 62 | F03 builds the stored pull request readers it uses (merge date, number); by author, reviews and comments go to F04 and F05 | Session |
| 63 | One call at a time with a pause, three retries, generic User-Agent, `ei.openproject` properties | Session |
| 64 | "Done in the quarter" read from activity status texts, matched against the known names | Session |
| 65 | References to hidden items kept as `not_visible` links; F10 decides how they count | Session |
| 66 | Job `openproject-import` arguments; first run 1 to 14 Jul 2026, quarter 2026-Q3 | Session |
| 67 | Done statuses: `tested`, `deployed`, `resolved`, `released`, `closed` (Q1, option "QA passed") | User |
| 68 | Pull all pull requests of Features done in the quarter only, not the window (Q2) | User |
| 69 | The OpenProject pull request list is stored as a reduced copy of stable fields (Q3) | User |

The question brief was asked on 19 Nov 2026, one question at a time; the user took the recommended option of each.
