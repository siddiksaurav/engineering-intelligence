# F08 Blind check: design

State: draft

## Goal

The measurement behind "I validated the idea" (master plan section 1, points 2 and 3, decision 308). The user labels a balanced sample of Feature work items with a complexity, without seeing the AI's answer. Then the user reads the AI's reason for each labeled item and marks it right or wrong. The system gives three numbers: complexity exact match, complexity within one step, reason right. They and every disagreement go into `docs/feasibility-report.md`.

## In scope / Out of scope

**In.**
- Migration V15: the tables `blind_check_item`, `human_label` and `reason_mark`, and two settings.
- A new backend package `review`: picking the sample, the CSV export, the CSV import, storing a reason mark, the numbers.
- The item page shows the user's label next to the AI's judgment, and takes the reason mark.
- The numbers through `GET /api/blind-check`.
- A labeling guide for the user, `docs/blind-check-labeling-guide.md`.
- `docs/feasibility-report.md`, written when the labels and marks are in.

**Out.**
- Work type labels, repeat labeling, a second AI run, the size check, the review comment sample (`poc-plan.md` section 4; not planned, decision 308).
- The screen where a lead corrects a judgment (master plan section 4). A reason mark changes no judgment and no score.
- Authentication. There is one labeler, the user.
- A new prompt version. Whether a tuning round follows is question Q8.
- F09. Every complexity has 6 or more items (decision 307).

## Data and tables

### Spec rules this feature uses (v1.6 section 6.2, copied)

The depth of a problem:

| Depth | Meaning | Example |
| --- | --- | --- |
| Routine | The project already has a solved example of this problem, or it is standard practice that every developer on that stack knows | A create, read, update and delete screen. A second datatable built like the first one |
| Answer exists | The tool or a well-known pattern covers the problem, but the project had no earlier example. The person had to find the answer and apply it | Streaming a large file in a tool that supports streaming. Storing hashes to discard duplicates |
| No ready answer | The tool does not support it and no known pattern fits. The person had to do R&D and work out a solution | A map that drills down from country to district in a dashboard tool that has no such chart |

The complexity from the depths:

| Complexity | When it applies | Tier |
| --- | --- | --- |
| Low | Every problem is routine | T1 |
| Medium | The deepest problem is "answer exists" | T2 |
| High | At least one problem has no ready answer. Or several "answer exists" problems affect each other. Or the work is a design whose mistakes stay hidden across the system, such as a permission model | T3 |
| Very high | The problems reach across projects or the organization | T4, for later use |

"Two problems affect each other when two different people could not have built and tested them separately."

The five reference examples: Organization create, read, update and delete (all routine, Low); first datatable on the project with a proper query (two answer exists, Medium); role-based permission design (three answer exists, affect each other, mistakes stay hidden, High); file ingestion through NiFi from scratch (four answer exists, affect each other, mistakes stay hidden, High); clickable map with drill-down in Superset (one no ready answer, one answer exists, one routine, High).

The rules: size is not complexity; the first solution gets the credit; new to the person is not new to the project; merged means accepted; the work must leave a trace; complexity does not depend on how the tickets were cut.

Where the PoC differs, on purpose: the AI does not search the repository for an earlier example (decision 303) and assumes there was none (decision 320); the AI decides the complexity itself and the table is guidance (decision 323). The user labels by the spec as written, with the repository open. A difference that comes from these two relaxations is a finding of the report, not an error of the check.

### `poc-plan.md` section 4, the part in force (decision 308)

The system exports the sample as a CSV with the item id and the links to GitHub and OpenProject, and without the AI's answer. The user fills in the complexity and the file is imported back. Only after that does the user open the items to see the AI's answer and mark whether its reason is factually right. This catches a correct answer given for a wrong reason.

Proposed targets: exact match 70% or more; within one step 95% or more; reason right 90% or more. With 30 items they are an indication, not proof.

### Facts about the data (`ei_latest`, checked 25 Nov 2026 by SQL)

- 42 current Feature work items, each with a current `feature_work_complexity` judgment of `feature-work-complexity/v2`, by 18 developers.
- The model's `complexity`: 13 low, 19 medium, 10 high, 0 very high. `complexityByTable`: 13 low, 16 medium, 13 high (3 items are medium by the model and high by the table).
- Pull requests: the 13 low items have 13, the 19 medium have 21, the 10 high have 28 (6 at most on one item). A balanced sample of 30 has about 50 pull requests to read.
- Problems listed: 23 on the low items, 51 on the medium, 43 on the high (117). A balanced sample of 30 has about 90.
- Thin evidence (`thinEvidence`): 6 items (1 low, 2 medium, 3 high). No work item (`fields.noWorkItem`): 4 items (3 low, 1 medium).
- `fields` of a Feature work item: `feature{number, title, type, url}` (absent with `noWorkItem: true`), `tasks[]`, `pullRequests[]{number, title, url, mergedAt, externalId}`, `doneAt`.
- The newest migration is V14. `/data/` is in `.gitignore` ("Local data exports (blind-check sheets may hold labels)").

### New tables (migration V15)

All three are append-only: a trigger refuses every update and delete, as on `judgment`. A correction is a new row with `supersedes_id`. `AppendOnlyTablesTest` gets a test for each, and `DatabaseTest` empties them.

`blind_check_item`: the sample. One row per picked item.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint identity | |
| `evidence_item_id` | bigint, unique, references `evidence_item` | the picked Feature work item |
| `judgment_id` | bigint, references `judgment` | the AI judgment the check measures, fixed when the sample is picked (decision 329) |
| `position` | int, unique | the row of the item in the CSV, 1 to n |
| `picked_at` | timestamptz | |

`human_label`: the user's blind label.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint identity | |
| `blind_check_item_id` | bigint, references `blind_check_item` | |
| `complexity` | text, null or one of `low`, `medium`, `high`, `very_high` | null only with `cannot_judge` (Q5) |
| `cannot_judge` | boolean | the user could not label the item (Q5) |
| `note` | text, nullable | the user's own words from the CSV |
| `labeled_at` | timestamptz | the time of the import |
| `supersedes_id` | bigint, unique, references `human_label` | |

`reason_mark`: the user's verdict on the AI's reason, made after the label.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint identity | |
| `blind_check_item_id` | bigint, references `blind_check_item` | |
| `reason_right` | boolean | the AI's reason is right |
| `problem_marks` | jsonb array | one entry per problem of the judgment, in its order: `{"name": ..., "verdict": "right" / "should_be_routine" / "should_be_answer_exists" / "should_be_no_ready_answer" / "not_a_problem"}`; empty when Q2 is answered A |
| `note` | text, nullable | |
| `marked_at` | timestamptz | |
| `supersedes_id` | bigint, unique, references `reason_mark` | a changed mark is a new row |

The current label or mark of an item is the row no other row supersedes.

### New settings (migration V15, from 2026-Q2)

- `blind_check.items_per_complexity`: `10`. How many items of each of low, medium and high are picked.
- `blind_check.targets`: `{"exactMatch": 0.70, "withinOneStep": 0.95, "reasonRight": 0.90}` (Q7).

## Main flows

All of it runs against the database the backend is started on; for the real check that is `ei_latest`.

**1. Pick the sample.** `POST /api/blind-check/sample`. Reads every current Feature work item with a current `feature_work_complexity` judgment. Per value low, medium and high it takes `blind_check.items_per_complexity` items, in the order of a hash of the item id (a fixed, arbitrary order that does not depend on the complexity or the developer). If a value has fewer items, the call is refused and names the value and the count. The picked items get their `position` from the same hash order across the whole sample, so the order of the rows says nothing about the AI's value. The rows are stored once. A second call changes nothing and answers with the stored sample (decision 330). On `ei_latest`: all 10 high, 10 of 13 low, 10 of 19 medium (Q3, Q4).

**2. Export.** `GET /api/blind-check/sample.csv`. One row per `blind_check_item` in `position` order, RFC 4180, UTF-8. Columns (decision 331):

| Column | Content |
| --- | --- |
| `item_id` | the evidence item id |
| `developer` | the person's display name: the label is for this person's part of the feature |
| `title` | the feature title, or the first PR title for an item with no work item |
| `work_item` | the OpenProject link; empty for an item with no work item |
| `pull_requests` | the GitHub links of the person's PRs, separated by one space |
| `complexity` | empty; the user fills in `low`, `medium`, `high`, `very_high` (or `cannot_judge`, Q5) |
| `note` | empty; free text |

No column comes from `judgment`. The export reads `judgment` only to know nothing: it does not join it at all; `blind_check_item` already holds the sample.

**3. Label.** The user fills in the file with `docs/blind-check-labeling-guide.md` beside it (the tables and rules above, how to fill in the columns, and the warning not to open item pages or profiles). About 3 hours for 30 items.

**4. Import.** `POST /api/blind-check/labels` with the CSV as the body (`text/csv`). Rules (decisions 332, 333):
- The header must hold `item_id`, `complexity` and `note`; other columns are ignored, so the file can come back as it was exported.
- Upper and lower case and spaces around a value are accepted (`Medium`, ` very high `, `very_high`).
- A row with an empty `complexity` is "not labeled yet" and is skipped. The file can be imported in parts.
- All or nothing. If any row is wrong, nothing is stored and the answer is `400` with one line per problem: the line number, the item id, the value, and what is allowed. Wrong means: an `item_id` that is not a number or not in the sample, an item twice in the file, a `complexity` outside the allowed values.
- A label is given once. A row whose item already has a label with another value is wrong ("item 17 is already labeled medium; a label is not changed after the AI's answer could be seen"). The same value again is skipped.
- The answer counts: labeled now, already labeled, not labeled yet.

**5. See both answers.** `GET /api/evidence-items/{id}` gains `blindCheck`: null for an item outside the sample; otherwise `{label: {complexity, cannotJudge, note, labeledAt} or null, reasonMark: {reasonRight, problemMarks, note, markedAt} or null}`. While a sampled item has no label, its `judgment` is answered with the state `hidden_until_labeled` and no other field (Q6). The item page shows "Your label" beside "AI judgment" with a line "same", "one step apart" or "two or more steps apart".

**6. Mark the reason.** On the item page of a labeled item, a form under the AI judgment: "The AI's reason is right" yes or no, per problem a verdict on its depth (Q2), a note, Save. The form posts to a Next.js server action, which calls `PUT /api/evidence-items/{id}/reason-mark` (the browser still never calls the backend; decision 336). The backend refuses a mark for an item outside the sample (404) or without a label (409), and a `problem_marks` list that does not match the problems of the pinned judgment (400). A second save writes a new row that supersedes the first.

**7. The numbers.** `GET /api/blind-check`, computed by SQL on every call, nothing stored (decision 337):
- `sampled`, `labeled`, `cannotJudge`, `reasonMarked`.
- `exactMatch`, `withinOneStep`: count, of how many, share. The AI's value is `value.complexity` of the pinned judgment (decision 334). The order is low, medium, high, very high; within one step means at most one place apart (decision 335). The denominator is the items labeled with a complexity.
- `tableExactMatch`: the same against `value.complexityByTable`.
- `reasonRight`: count, of the items marked, share.
- `confusion`: the count per pair (user's label, AI's complexity).
- `problemVerdicts`: the count per verdict over all marked problems (Q2 B).
- `thinEvidence`: exact match and reason right for the items with `thinEvidence` apart.
- `targets` from the setting, and `disagreements`: every item where the label differs or the reason is marked wrong, with item id, title, both values, the user's note, the AI's reason and the mark's note.

**8. The report.** `docs/feasibility-report.md`, written by a session from `GET /api/blind-check` when all labels and marks are in: what was measured and how, the three numbers against the targets, the confusion table, every disagreement with both answers, what the relaxations of decisions 303, 320 and 323 show, and what the PoC did not validate (the earlier-example search, DSi's own data, levels, the other dimensions, one labeler with no repeat labeling, 30 items).

**Uses.** `evidence.EvidenceItem`, `CurrentEvidenceItems`; `judgment.CurrentItemJudgments`, `JudgmentKind.FEATURE_WORK_COMPLEXITY`; `api.ItemTitleAndLinks.titleOf` and `linksOf`, `EvidenceItemSummaries.detailOf`, `ItemJudgmentResponse`; `setting.SettingService`; web `app/items/[itemId]/page.tsx`, `JudgmentView.tsx`, `ProblemsSolved.tsx`, `lib/items.ts`, `lib/featureWorkProblems.ts`, `lib/backend.ts`.

**Gives.** Tables `blind_check_item`, `human_label`, `reason_mark`. Endpoints `POST /api/blind-check/sample`, `GET /api/blind-check/sample.csv`, `POST /api/blind-check/labels`, `PUT /api/evidence-items/{id}/reason-mark`, `GET /api/blind-check`. `docs/feasibility-report.md`.

## Edge cases

- Fewer items of a value than the setting asks for: the sample is refused, nothing stored.
- A sampled item is superseded by a later `evidence-build`, or judged again by a later prompt: the sample keeps its item row and its pinned judgment; the numbers do not move.
- A file saved by a spreadsheet: a byte order mark, `;` as separator, quoted cells and line breaks inside a cell are read. A separator that is neither `,` nor `;` is refused with a message that names the header found.
- A title with a comma, a quote or a line break is quoted on export.
- The user labels `very_high` where the AI said high: one step apart.
- `cannot_judge`: counted and listed, outside every denominator; no reason mark is asked.
- An item with thin evidence or no work item: exported like any other, with an empty `work_item` cell when there is none.
- The numbers before any label: every share is null, not 0.
- A reason mark on a v1 judgment (no problems): cannot happen in the sample; the backend accepts an empty `problem_marks` only when the judgment has no problems.

## How it will be tested

Master plan "Done when":

1. **The exported CSV contains no AI answer in any column.** Test: an export of items judged low, medium and high holds none of the words of their judgments (complexity, depth, reason text, model) in any cell, and the header is exactly the seven columns. On `ei_latest`: the exported file is searched for every `reason` and `value` text of the 30 pinned judgments.
2. **The item page of a labeled item shows both answers.** Web test of the item page with a label; backend test of `blindCheck` on the detail; by eye on one item of `ei_latest`.
3. **Each number equals a count made with separate SQL.** After the import and the marks, `GET /api/blind-check` is compared with hand-written SQL counts on `ei_latest`; the commands and outputs go into `notes.md`.
4. **The feasibility report is written and the user has read it.** Waiting for the user.

Other checks: the sample is balanced and stable (second call, same rows); the row order is not grouped by complexity; the import is all or nothing and names every wrong row; a label is not changed by a second import; the judgment is hidden until the label is in; a reason mark supersedes; the three tables refuse update and delete.

## Open points

### Question brief

**1. Business questions**

Q1. What do you fill in for each item?
A. A complexity, and a free note if you want. Fast; your reasons are in the notes only where you write one.
B. A complexity and the depth of the hardest problem. A little slower; shows whether a disagreement comes from the depth or from the step to the complexity.
C. A complexity and your own list of problems with depths. The richest comparison, but about twice the labeling time.
Recommended: A. It is what the plan asks, and Q2 gets the depth detail from your reading of the AI's reasons.

Q2. How do you mark the AI's reason after the labels are in?
A. One mark per item: right or wrong, with a note.
B. One mark per item, and for each problem the AI listed whether its depth is right (or which depth it should be, or "not a real problem"). About 90 problems on 30 items.
Recommended: B. It shows where the AI goes wrong, above all whether it calls routine work "answer exists" (decision 320).

Q3. Which items are in the check?
A. 30, balanced: all 10 High, 10 of the 13 Low, 10 of the 19 Medium. About 3 hours.
B. All 42. About 4.5 hours; no sampling, and the numbers reflect the real spread.
Recommended: A. It is the decided size (decision 308) and keeps High from being rare.

Q4. Six items have thin evidence (the AI had no ticket or no description). Three of them are High.
A. They can be picked like any other; the report gives their numbers apart.
B. They are left out. High then has 7 items, so the sample is 7, 7, 7 or uneven.
Recommended: A. Leaving them out makes the sample too small.

Q5. May you answer "cannot judge" for an item?
A. Yes. It is counted and listed, and left out of the percentages.
B. No. Every item gets a complexity.
Recommended: A. A forced guess makes the agreement number worse for no reason.

Q6. Should the item page hide the AI's answer of a sampled item until its label is imported?
A. Yes: "hidden until your label is imported". Profiles and lists still show complexities, so they stay closed.
B. No, the order is kept by discipline alone.
Recommended: A. A cheap guard against spoiling the check by one wrong click.

Q7. Which targets does the report measure against?
A. Exact match 70%, within one step 95%, reason right 90%, fixed now, before labeling.
B. No targets; the report gives the numbers and you judge.
Recommended: A. A target set after seeing the result is worth little.

Q8. What happens after a poor result?
A. F08 ends with the report. Whether a prompt tuning round follows is decided then, as its own piece of work.
B. F08 includes one tuning round: a new prompt version, measured on the 12 items left over, which you then also label.
Recommended: A. The report must first show what is wrong; 12 items are too few to measure a fix.

Q9. Where do you see the numbers?
A. A "Blind check" page in the web app (numbers against targets, the table of your label against the AI's, the disagreements with links) and the report.
B. In the report only.
Recommended: A. The disagreement list with links is how you will work through the reasons.

**2. Technical decisions I need your direction on**

Q10. How do you get the file and give it back?
A. Download and upload buttons on the Blind check page.
B. Two `curl` commands (the session can run them for you; the file lies in `data/blind-check/`).
C. No file: a labeling page in the web app that shows the links and takes your answer. Easier on a phone, but it changes the plan (the CSV is a "Done when" line) and is more to build.
Recommended: A, if Q9 is A; otherwise B.

Q11. Labeling takes you about 3 hours, after the code is built. How does F08 close?
A. The tooling is built, reviewed and merged now. You label and mark. A short later session checks the numbers against SQL and writes the report. F08 stays "in progress, waiting for labels" until then.
B. The branch stays open until the labels are in, and everything merges at once.
Recommended: A. Nothing waits on an open branch.

**3. Decisions I made myself (overrule any by number)**

D328. Three append-only tables in a new package `review`: `blind_check_item`, `human_label`, `reason_mark`.
D329. The sample fixes the judgment it measures; a later judgment of the same item does not move the numbers.
D330. The sample is picked once, in the order of a hash of the item id, and stored; the CSV rows are in that order, not grouped by complexity.
D331. CSV columns: `item_id`, `developer`, `title`, `work_item`, `pull_requests`, `complexity`, `note`.
D332. The import is all or nothing and names every wrong row; an empty complexity means "not labeled yet".
D333. A label is given once; an import cannot change it.
D334. The headline numbers compare your label with the AI's own complexity; agreement with the spec's table is a second line.
D335. Within one step: at most one place apart in low, medium, high, very high. Denominator: the items labeled with a complexity.
D336. The reason mark is saved from the item page through the web server; the browser still never calls the backend.
D337. The numbers are computed on every call; nothing is stored.
D338. Sample, export and import are plain endpoints, not jobs: they are short and carry a file.
D339. The sample size per complexity and the targets are rows in `setting`.
D340. A labeling guide, `docs/blind-check-labeling-guide.md`, carries the tables and rules of spec 6.2.
D341. No labeler column: there is one labeler and no login.

Reply with letters (Q1 B, Q2 A ...), "OK" for all recommendations, or your own answer.
