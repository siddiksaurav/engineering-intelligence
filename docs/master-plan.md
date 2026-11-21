# Engineering Intelligence PoC: master plan

This document lists what will be built, in which order, and where each part stands. It is the map. It does not hold the detail of any feature. Each feature gets its own design and task plan before any code is written (see `docs/features/README.md`).

| Document | What it holds |
| --- | --- |
| `docs/spec/spec-v1.5.md` | The full system spec. Never edited |
| `poc-plan.md` | The PoC design: what the real data contains, scope, building rules, data model, risks |
| `docs/master-plan.md` | This document: features, order, status |
| `docs/features/FNN-name/` | Per feature: `design.md`, `plan.md`, `notes.md` |
| `docs/decisions.md` | Every decision, with date and reason |
| `docs/progress.md` | What was done in each session and what comes next |

When two documents disagree, the order of authority is: `docs/decisions.md` (newest entry), then the feature's approved `design.md`, then this master plan, then `poc-plan.md`, then the spec. `CLAUDE.md` only summarizes; if it disagrees with any of these, it is wrong and is corrected. A session that finds a disagreement reports it to the user and fixes the lower document. The PoC differs from the spec on purpose in several places; `poc-plan.md` section 5 lists them.

## 1. Goal

The spec puts all judgment in one step: the AI sets work type, complexity and tier on each evidence item. Everything after that is counting. The PoC must show management two things on real, public data:

1. The AI's judgment is correct often enough to trust.
2. A person can follow any score back to the pull request it came from.

The data is public: the repository `github.com/opf/openproject` and its tracker `community.openproject.org`. The PoC is the first slice of the real system, not throwaway code.

The finished PoC is scope option B of `poc-plan.md` section 2. A viewer opens the profile page of one developer and sees two dimension scores, five analytics and two gates. Every number opens to its evidence items, each item opens to the AI's judgment with its reason, and each judgment links to the source on GitHub and OpenProject.

## 2. Global constraints

Every feature follows these. A feature design may not change one without a new entry in `docs/decisions.md`.

**Stack**
- Java 25. Spring Boot 4.x. Maven.
- PostgreSQL, run with Docker Compose. Schema changes only through Flyway migrations.
- Next.js with the App Router and TypeScript for the web front end.
- One repository: `backend/` for the Spring Boot application, `web/` for Next.js.
- Exact versions are fixed in F01 and recorded in `docs/decisions.md`. They are looked up at that time, not taken from memory.

**Data**
- Public data only. GitHub through the GraphQL API with a read-only token. OpenProject through API v3 without login.
- Window: one month, 1 to 31 Jul 2026 (decision 78). The quarter is still named 2026-Q3 in `setting` and on the screens; only July's data is in it. For every Feature linked from a PR of the window that reached a done status in the quarter, all its linked PRs are pulled whatever their date (decision 68, unchanged; this is what `openproject-import` does).
- LLM: a free hosted model (Groq Cloud first) through an OpenAI-compatible API, behind `JudgmentModel` (decision 79). Free tiers limit requests and tokens per minute and per day; F06 measures them and the runs respect them.
- Real developer names are shown.

**Rules that never change**
- `raw_record` is an unchanged copy of the source. It is never edited.
- `evidence_item` and `judgment` are append-only. A correction is a new row with `supersedes_id`.
- The tier lives in `judgment`, not on the evidence item. The current tier of an item is its latest judgment.
- Every judgment stores its reason, the facts it rests on with source links, the model name, the prompt version and a hash of the input. The same input hash is never judged twice.
- The person on every evidence item is the PR author. Code written with AI belongs to the person who pushed it. The AI-usage labels on PRs are ignored.
- A work item of type Bug is never Feature work, even under a Feature. The tracker type decides this, not the AI.
- Lines of code are never given to the model when it judges complexity. Size is not complexity (spec 6.2).
- For Feature work the model judges complexity only. The tier is read from the table in spec 6.2: Low is T1, Medium is T2, High is T3, Very high counts as T3 with weight 4.
- The model is reached only through the `JudgmentModel` interface.
- No number from the spec is hard-coded. Thresholds, weights and done statuses are rows in `setting` (spec 14).
- Bot accounts (dependabot, the CI account, the Copilot review bot) and an author's replies in their own PR never produce evidence items.

**Way of working**
- Test first. Each task in a feature plan has its test before its code.
- One commit per task.
- A feature is shipped with `/ship FNN` (`CLAUDE.md`, "How work is done", decision 72). The user's answer to the question brief counts as design and plan approval.
- Secrets (GitHub token, LLM API key) live in a local `.env` that is not committed. `.env.example` lists the names.
- Jobs are started by a REST endpoint, and every run is recorded. There is no command-line runner and no scheduler.

## 3. System overview

```
GitHub ─┐
        ├─> source/ ──> raw_record ──> evidence/ ──> evidence_item ──┐
OpenProject ┘                              │                          ├─> analytics/ ─┐
                                           └─> judgment/ ──> judgment ┘               ├─> api/ ──> web/
                                                                      └─> scoring/ ───┘
```

| Package (`backend/`) | Does | Writes | Spec step |
| --- | --- | --- | --- |
| `source/` | Connectors for GitHub and OpenProject | `data_source`, `raw_record`, `project` | Step 1 (section 5) |
| `identity/` | Persons and their outside identities | `person`, `person_identity` | Step 1 |
| `evidence/` | One builder per evidence type | `evidence_item`, `evidence_item_source`, `evidence_item_link` | Step 2 (section 6) |
| `judgment/` | `JudgmentModel`, prompts, the judgment store | `judgment` | Step 2 |
| `analytics/` | One query per analytic. Nothing is stored | none | Step 3 (section 7) |
| `scoring/` | Mapping, presence, working tier, gates, dimension score | `dimension`, `dimension_evidence_type`, `dimension_score`, scoring rows in `setting` | Steps 4 and 5 (sections 8, 9) |
| `review/` | Blind-label export and import | `human_label` | PoC only |
| `api/` | REST for the web front end | none | |
| `common/` | `Quarter`, the canonical form of JSON and the content hash | none | |
| `job/` | A job, the launcher that runs it and records the run, the endpoint that starts it | `job_run` | |
| `setting/` | Reading a setting for a quarter | `setting` | Section 14 |
| `web/` (Next.js) | Evidence list, item page, profile page | none | Step 6 (section 10) |

The tables are described in `poc-plan.md` section 3.4. Adding a data source later means a new connector. Adding an evidence type means a new builder, a prompt and a row in the mapping.

## 4. Out of scope

These are left out on purpose (`poc-plan.md` 3.1). No feature builds them. If a session thinks one is needed, it stops and asks.

- Gates G3 and G5. G4 is shown only as "gate unverified".
- The clean-delivery rule (spec 9.2).
- Dimensions other than Coding and code quality, and Engineering standards and system health.
- Tiers for Bug fix and Documentation items. They are captured and counted only.
- Confidence (spec 9.4), the opportunity profile (spec 6.6), the weighted overall score (spec 10.1), lead-rated dimensions (spec 8.5, 9.5).
- The screen where a lead corrects a judgment (spec 9.7 row 1). The data model already supports corrections.
- Authentication and roles.
- A scheduler or daily run.
- The client-owned project track (spec 15).
- The demo. It is planned separately.

## 5. Features

| Id | Feature | Depends on | Visible result |
| --- | --- | --- | --- |
| F01 | Repository skeleton | none | The application starts, the database is migrated, the web shell opens |
| F02 | GitHub connector | F01 | Merged PRs of two weeks are in `raw_record` |
| F03 | OpenProject connector | F02 | Work items of those PRs, with history and parents, are in `raw_record` |
| F04 | Identity | F02, F03 | Each PR author is one person with a GitHub login and a proposed OpenProject user |
| F05 | Evidence builders without AI, and the evidence list page | F04 | A web page lists the evidence items per developer with links to the sources |
| F06 | `JudgmentModel`, Stage 1 PR digest, builders that need the PR work type | F05 | Every PR has a digest; test, refactor, security and infra items exist with a tier |
| F07 | Stage 2 Feature work judgment and review comment judgment | F06 | Every Feature work item has a complexity and every review comment a type and tier |
| F08 | Blind check and the item page | F07 | You label 30 items blind; the item page shows source, AI judgment and your label |
| F09 | Full-window run (one month) | F08 | July 2026 is ingested and judged |
| F10 | Five analytics and the hygiene number | F09 | The analytics API returns the numbers for one developer |
| F11 | Scoring | F10 | Two dimension scores per developer, with gates G1 and G2 and the trail |
| F12 | Profile page | F11 | One page per developer where every number opens down to a PR |
| F13 | Accuracy measurement (dropped, decision 80) | F12 | An accuracy page with the measures of `poc-plan.md` section 4 |

Each block below gives a new session enough to start the deep dive. "Open questions" are the points the deep dive must answer; they are not decided here.

---

### F01 Repository skeleton

**Goal.** A repository where every later feature has a place to put its code and a way to run its tests.

**Builds.**
- `backend/`: a Spring Boot 4.x application on Java 25 with Maven, the package layout of section 3, a health endpoint, and a test setup that runs against a real PostgreSQL.
- Flyway migration for `data_source`, `raw_record`, `person`, `person_identity`, `project`, and `setting` (the table and the way to read a value for a quarter; later features add their own rows, for example the done statuses in F03 and the weights in F11).
- `docker-compose.yml` with PostgreSQL.
- `web/`: a Next.js shell with one page that calls the backend health endpoint.
- `.env.example`, and the build, test and run commands written into `CLAUDE.md`.

**Read first.** `poc-plan.md` 3.4, 3.5, 3.6.

**Gives to later features.** The `raw_record` table and its repository class; the `setting` table and a way to read a setting; the `Job` interface and the registry of jobs by name; the test setup.

**Done when.** One command starts the database; one command builds and tests the backend with all tests green; the web shell shows "backend up"; a second start does not run the migrations again.

**Open questions.** Exact versions of Spring Boot, PostgreSQL and Next.js. Spring Data JDBC or JPA. How tests get a database (Testcontainers or the Compose database). Columns of `raw_record` (record kind, external id, URL, content hash, payload JSONB, fetched at). How JDK 25 is installed on this machine: it has JDK 21 only, and installing needs the user's approval.

---

### F02 GitHub connector

**Goal.** An unchanged copy of every merged PR in the window, with what is needed to judge it.

**Builds.** A connector that reads `opf/openproject` through the GitHub GraphQL API and stores, per merged PR: title, body, author, labels, branch name, merge date, file list, reviews, inline review comments with file path and diff hunk, and the diff. It runs for a range of merge dates, or for a given list of PR numbers (F03 needs this for the PRs of long features). It can be run again without creating duplicates. It is first run on two weeks. It also builds the rest of the job mechanism: the launcher, the `job_run` table and the endpoint that starts a job. The GitHub import is its first job (F01 design, section 13).

**Read first.** `poc-plan.md` section 1 (GitHub), 3.1 item 1, 3.7 step 2. Spec section 5, source 1.

**Gives to later features.** `raw_record` rows for each PR, its diff, reviews, review comments and conversation comments, with external id, URL and content hash (layout: F02 design 6.2). The job `github-import`, which fetches PRs by merge date or by number. The bot list in setting `github.bot_logins`. The job launcher, `job_run` and the endpoint. Readers that list stored PRs by merge date or author are built by F03, their first user (decision 46).

**Done when.** The row count of merged PRs for the two weeks equals the count GitHub's search shows for the same range. A second run adds no rows. A changed PR is stored as a new version, and the old one is kept.

**Known facts.** About 100 human-merged PRs per week from about 36 authors, plus about 30 bot PRs per week. The unauthenticated limit of 60 requests per hour is too low; a read-only token is required.

**Open questions.** How the diff is fetched (GraphQL does not return patch text; the REST endpoint does) and how large diffs are stored. The list of bot accounts (dependabot, the CI account, the Copilot review bot, others found in the data) and where it is kept; F04 and F05 use the same list. Whether bot PRs are stored and marked, or skipped. One `raw_record` row per PR with everything inside, or one row per PR, review, comment and file. Rate-limit handling and resume after a failure. How a re-fetched, changed record is versioned in a table that is never edited. For the job mechanism: whether a second run of a job may start while one is running, what a run records as counts, and who may call the endpoint while there is no authentication.

---

### F03 OpenProject connector

**Goal.** The work items behind the PRs, with enough history to know the feature, the people and the done date.

**Builds.** A connector that reads `community.openproject.org/api/v3` without login and stores: every work item referenced by a stored PR, its ancestors up to the top, its children, its activities (status history with who and when), its custom fields "Developers" and "Module", and its list of linked GitHub PRs. For each Feature that reached a done status in the quarter, it also has F02 pull all linked PRs whatever their date. It fills `project`. It also builds the readers of the stored GitHub rows that it uses: list PRs by merge date and by number (decisions 46 and 62; row layout in F02 design 6.2). Readers by author and of reviews and comments are built by F04 and F05. F02's `github-import` job fetches PRs by number with `pullRequestNumbers`.

**Read first.** `poc-plan.md` section 1 (OpenProject and the "missing or weak" table), 3.1 item 1, 3.7 step 3. Spec section 5, source 2.

**Gives to later features.** `raw_record` rows for work items and activities. A resolved link between each PR and its work item. A way to walk from a work item to its nearest ancestor of type Feature.

**Done when.** About 78% of human-merged PRs in the test range resolve to a work item. Both forms of id (the number 81224 and the key COMMS-1078) resolve to the same item, and so does an old key after a move (SE-694). A second run adds no rows.

**Known facts.** The link is almost always a URL in the PR body. Types: Bug, Feature, Implementation, Epic, Code maintenance, Documentation, Task, Design Task. Hierarchy: Epic, then Feature, then Implementation, Task or Bug. About half of items have a parent. The assignee is cleared at hand-over and is not reliable.

**Open questions.** The exact patterns that link a PR to a work item (URL forms, branch names). How "reached a done status in the quarter" is read from the activities, and which statuses count as done (a setting). `poc-plan.md` 3.1 says "done in the quarter" and 3.7 step 3 says "done in the window"; this plan follows 3.1 (the quarter), to be confirmed. API paging and politeness limits on a public server. Whether work items are also pulled by update date, or only when a PR points to them.

---

### F04 Identity

**Goal.** One person per developer, however many outside identities they have.

**Builds.** Persons are created from every human GitHub account that wrote a PR, review or comment (decision 92). For each person the system proposes the OpenProject user, taken from the "Developers" field and the status history of the work items their PRs link to. Bot accounts are marked and never become persons. `level` stays empty.

**Read first.** `poc-plan.md` 3.4 (`person`, `person_identity`), 3.7 step 4. Spec section 5, "One identity per person".

**Gives to later features.** `person` and `person_identity`. A function from a GitHub login to a person, and from an OpenProject user id to a person.

**Done when.** Every human PR author in the test range is exactly one person. No bot is a person. Each proposed OpenProject match shows the evidence it rests on, and a match can be confirmed or rejected by hand.

**Open questions.** The rule that proposes an OpenProject user, and how sure it must be. How a match is confirmed in a PoC with no screens for it (a file, a SQL script or a small endpoint).

---

### F05 Evidence builders without AI, and the evidence list page

**Goal.** The first visible result: evidence items built by rule alone, listed per developer.

**Builds.**
- Migration for `evidence_item`, `evidence_item_source`, `evidence_item_link`.
- Builder **Feature work** for PRs with a work item: for each merged PR, follow its work item up to the nearest ancestor of type Feature, or the item itself if there is none. One item is one PR author plus one such feature plus all that author's PRs and child items under it. Work items of type Bug are left out of the group.
- Builder **Bug fix**: a work item of type Bug with the PRs that fixed it, even under a Feature.
- Builder **Review comment written**: one item per human reviewer comment, without bot comments and the author's own replies. This type has no row in the building table of `poc-plan.md` 3.2; its rule is in 3.2 "Review comments" and in section 1.
- API and a web page that lists evidence items per developer with links to GitHub and OpenProject.

**Read first.** `poc-plan.md` 3.2 "Building rules" (the whole table), 3.4, 3.7 step 5. Spec 6.1, 6.2 "Feature work", 6.5.

**Gives to later features.** Evidence items with their source records. The rule for which quarter a Feature work item counts in (when the feature reaches a done status). The list page that F08 extends.

**Done when.** 20 Feature work groups have the right person, PRs and feature: the session lists them with their links and the user checks them against GitHub and OpenProject. No item whose tracker type is Bug sits in a Feature work group. Building twice gives the same items and no duplicates.

**Open questions.** Overlap with F06, which must be settled here: by the rule above every PR with a non-Bug work item joins a Feature work group, but `poc-plan.md` 3.2 also makes a Refactor, Security or Infra item from a PR with that work type whose work item is not under a Feature. The work type is not known until F06. Decide how a PR ends in exactly one of the two (for example: groups with no Feature ancestor stay provisional until the PR digest exists). Whether a review's summary text counts as a review comment, or only inline comments. How a rebuilt item relates to the earlier one when its PR set grows (new row with `supersedes_id`, or a stable key). The type-specific fields in the JSONB column for these three types (spec 6.5). Stacked PRs ("1/5" to "5/5") on one feature: confirm they fall into one group. What the date of a Feature work item is. Whether Review comment written counts in the quarter of the comment or of the PR merge.

---

### F06 `JudgmentModel`, Stage 1 PR digest, and the builders that need the PR work type

**Goal.** A short, factual digest of every PR, and the evidence items that depend on the PR's work type.

**Builds.**
- Migration for `judgment`.
- The `JudgmentModel` interface and one implementation for a free hosted model through an OpenAI-compatible API (Groq Cloud first, decision 79), with JSON-schema structured output where the model supports it. The base URL, model and key come from `.env`, so another provider needs no code change.
- **Stage 1, PR digest.** One call per PR. Input: title, body, file list, and the diff with generated files, lock files and vendored code removed. Output: work type of the PR (one of the 13 in spec 6.2), modules touched, schema or data migration yes or no, new integration yes or no, nature of the change, tests included yes or no, test kind (unit, integration, regression, CI gate), a short reason. For the test change in a PR, and for a PR whose work type is refactor, performance, security or infra, also a tier from the rows of spec 6.4, with a reason.
- Builders: **Test change** (one per PR that changes tests), **Refactor and performance item**, **Security item**, **Infra and release item** (a PR with that work type whose work item is not under a Feature), **Documentation** (a PR with no work item whose work type is docs), **Feature work marked "no work item"** (a PR with no work item whose work type is feature or enhancement), **Bug fix** for a PR with no work item whose work type is fix. A PR with no work item and work type maintenance, technical task, research or operational support gets no evidence item and keeps its work type.
- A measurement of cost and time on 50 PRs.

**Read first.** `poc-plan.md` 3.2 (all of it), 3.4 (`judgment`), 3.7 step 6, section 5 risks "Very large PRs" and "Cost". Spec 6.2 "Work type", 6.3, 6.4 (rows Test change, Refactor and performance item, Security item, Infra and release item), 6.5.

**Gives to later features.** `JudgmentModel`. The judgment store with the skip-on-same-input-hash rule. The PR digest that Stage 2 reads. The file filter for diffs.

**Done when.** Every judgment row has a reason, at least one source link, a model and a prompt version. A second run with the same input makes no call. The time, tokens and rate limits met for 50 PRs are written into `notes.md`, with an estimate for the one-month window. A diff too large for one call is judged on what fits and marked "judged on partial code".

**Open questions.** Who sets the tier of a Test change: `poc-plan.md` 3.2 says both that the model gives it (Stage 1 text) and that it follows from the test kind (building table: unit in own PR is T1, integration or regression is T2, a test gate in CI is T3). Reading it from the test kind matches how Feature work is handled; decide. The other side of the F05 overlap: no PR may be in a Feature work group and also be a Refactor, Security or Infra item. The provider and model for Stage 1, and the current model ids and free-tier limits; check them at design time, not from memory. Whether the free tier's tokens per day are enough for the window, and what to do if not (a smaller model, a trimmed diff, a second provider, a paid tier). The exact JSON schema of the digest. The file filter rules. Where prompts live and how a prompt version is named. How tests run without calling the real model (a fake `JudgmentModel`) and how the real prompts are tried on a small fixed set. Whether calls run in parallel or as a batch.

---

### F07 Stage 2 Feature work judgment and review comment judgment

**Goal.** The judgment the whole PoC exists to show.

**Builds.**
- **Stage 2, Feature work.** One call per Feature work item with a strong model. Input: the work item texts (the feature and the person's child items), priority, linked items, the tracker's own type, and the PR digests. Lines of code are not given. Output: work type, complexity (Low, Medium, High, Very high), the rubric facts of spec 6.2 with a quote or link for each, a reason, and a confidence value. The tier is then read from the complexity table, not asked from the model.
- **Review comments.** One call per PR for all human reviewer comments, each with its diff hunk. Output per comment: comment type, substantive or style, tier (spec 6.4 row "Review comment written"), reason.

**Read first.** `poc-plan.md` 3.2 "The AI's two stages", section 5 "Changes I propose to the spec" numbers 3, 5 and 7. Spec 6.2 (complexity table and the two limits), 6.3, 6.4, 7.2 "Review effectiveness" for the comment types.

**Gives to later features.** A complexity and tier on every Feature work item, and a type and tier on every review comment, all in `judgment`.

**Done when.** Every Feature work item closed in the test range has one current complexity judgment with facts that each carry a quote or link. Every human reviewer comment has one current judgment. Module count alone does not decide complexity: a test case with a rename across five modules is judged Low.

**Open questions.** The model for Stage 2. The prompt text, including how the rubric of spec 6.2 is given and how the module-count rule is softened (proposed change 5). The list of comment types. What happens when a feature has more PR digests than fit in one call. How confidence is expressed and whether anything uses it.

---

### F08 Blind check and the item page

**Goal.** A first, honest check of the AI by a person, and the page where a judgment can be read next to its source.

**Builds.**
- Migration for `human_label`.
- Export of 30 Feature work items as a CSV with the item id and the links to GitHub and OpenProject, without the AI's answer. Import of the filled file (work type, complexity) into `human_label`.
- **Item page** (read-only): the source on the left (work item text, PR descriptions, files changed, links to GitHub and OpenProject); the AI's work type, complexity, tier, reason and facts on the right; the human label next to it where one exists.
- The list page of F05 now shows the AI's judgment per item, and how often AI and human label agree.
- Prompt tuning, at most one round (decision 80): the user reads further items, and the prompts of F06 and F07 are adjusted from what is found. Each change is a new prompt version, the affected items are judged again, and the old judgments are kept.

**Read first.** `poc-plan.md` 3.1 item 4, 3.7 step 8, section 4 (these 30 items are its tuning set). Spec 9.7.

**Gives to later features.** The export and import that F13 uses again. The item page that F12 links to.

**Done when.** The exported CSV contains no AI answer in any column. The item page for a labeled item shows both answers. The agreement number on the list page equals a count made by hand from the two tables.

**Order that must be kept.** The item page is opened to the user only after the labels are imported. Building it earlier is fine; showing AI answers before labeling is not.

**Decision at the end.** The user decides whether the agreement is good enough to start F09. F09 is the first large run, so it does not start without that decision.

**Open questions.** How the 30 items are picked (random, or balanced across the AI's complexity values). The CSV columns and how a wrong value in the import is reported. What the item page shows for the other evidence types. The chart library and page layout.

---

### F09 Full-window run (one month)

**Goal.** The one-month window ingested, built and judged.

**Builds.** Little new code. The connectors, builders and judgments run on 1 to 31 Jul 2026 (decision 78). What is added: progress output, resume after a failure, waiting on the free tier's rate limits, and a run report.

**Read first.** `poc-plan.md` 3.7 step 9, section 5 risk "Cost", the Verification section. `notes.md` of F02, F03 and F06 for the measured limits and costs.

**Gives to later features.** The complete data set that analytics and scoring read.

**Done when.** The number of distinct merged PRs and of distinct work items in `raw_record` matches the GitHub and OpenProject totals for the window. The share of PRs linked to a work item is about 78%. Every PR has a digest and every closed Feature work item a complexity, or a recorded reason why not. The actual cost is in `notes.md`.

**Known facts.** 215 merged PRs in 1 to 14 Jul 2026 (F02), so about 450 for the month.

**Open questions.** How the run waits on rate limits and resumes the next day if the daily quota runs out. What is done with PRs that failed judgment.

---

### F10 Five analytics and the hygiene number

**Goal.** The numbers that describe a developer's quarter. They score nothing.

**Builds.** One SQL query per analytic over `evidence_item` and `judgment`, and an API for them. Nothing is stored.

| Analytic | What it shows (spec 7.2) |
| --- | --- |
| Work classification | Closed items by work type, as shares |
| Work complexity | The person's Feature work by Low, Medium, High, next to the same for the project |
| Review participation, with Review effectiveness | PRs reviewed and different authors reviewed; substantive comments per PR reviewed and the mix of comment types. An approval with no substantive comment counts as zero |
| Review findings received | Substantive review comments received per PR, by type. Style comments are left out |
| Testing contribution | Share of feature and fix PRs that include test changes; share of Bug fix PRs that change tests |
| Hygiene (spec 10.3) | Share of PRs linked to a work item, for the repository |

Every count is split by tier (spec 7).

**Read first.** `poc-plan.md` 3.1 item 5, 3.4 last paragraph, 3.7 step 10. Spec 7 (opening), 7.2, 10.3.

**Gives to later features.** The analytics API for the profile page. Note that gate G1 in F11 is a different number from Testing contribution: G1 counts PRs with work type feature or enhancement, while Testing contribution counts feature and fix PRs (spec 7.2). F11 calculates its own gate shares.

**Done when.** For one developer, each number equals a count the session makes with separate SQL from the evidence list, shown to the user. The work-type shares add up to 100%. A PR with no evidence item still shows in Work classification.

**Open questions.** "Project" for the comparison bar in Work complexity: the repository stands in for it; confirm. Which items count as "closed in the quarter" for each analytic. The API shape.

---

### F11 Scoring

**Goal.** Two dimension scores per developer, each with a trail that explains it.

**Builds.**
- Migration and seed for `dimension`, `dimension_evidence_type`, `dimension_score`, and the scoring rows in `setting` (presence threshold, weights, gate limits, T3 items needed at L3), all versioned per quarter. The `setting` table itself exists since F01.
- **Presence and working tier** (spec 9.3). Frequent types (Feature work, Review comment written, Test change): presence at a tier when at least one third of the quarter's items of that type sit at that tier or higher. Feature work is counted by weight: T1 is 1, T2 is 2, T3 is 4. Rare types (all others): presence when at least one item sits at that tier or higher. The working tier of a dimension is the highest tier at which any of its evidence types has presence.
- **Score against a selected level.** Levels are unknown for this team, so the viewer selects a level. Working tier lower than the level is 0, same is 1, higher is 2 (spec 9.1 step 5).
- **Coding and code quality**: Feature work and Review comment written. No gates.
- **Engineering standards and system health**: Test change, Refactor and performance item, Security item, Infra and release item. At a selected level of L3 it needs two T3 items. Gates G1 and G2 apply. G4 is shown as "gate unverified". Bug fix items are listed on the trail as "counted, not tiered".
- **Gates G1 and G2** (spec 7.3, 9.1 step 6). G1: the share of the person's PRs with work type feature or enhancement that change tests. G2: the share of the person's Bug fix PRs that change tests. Each is compared with the same share for all developers in the repository in the quarter. Within 10 points passes. Lower by 10 to 25 points is a close miss: a 2 becomes 1. Lower by more than 25 points is a far miss: the score becomes 0. Fewer than three developers in the group gives "gate unverified" and no effect.
- The **trail**: the items, their tiers, the gate results and the notes ("held: G1", "gate: G2 far", "gate unverified"), stored with the score.

**Read first.** `poc-plan.md` 3.1 item 6, 3.3, 3.4, section 5 proposed change 13. Spec 7.3, 8.1, 8.3, 8.6, 9.1, 9.3, 9.6, 12.3, 14.

**Gives to later features.** `dimension_score` with its trail, and an API to read it for a person, a quarter and a selected level.

**Done when.** Unit tests reproduce the example of spec 9.3: 2 Low and 5 Medium give weights 12, 10 of them at T2 or higher (83%), working tier T2. Unit tests cover the gate limits at 10 and 25 points on both sides, and the fewer-than-three case. Changing a value in `setting` changes the result without a code change. A score can be recalculated from its trail by hand.

**Stand-ins, shown on the trail.** "Changes tests" stands in for "adds a regression test" in G2. The repository stands in for the spec's "project".

**No items.** Spec 8.3 marks both dimensions as possible "Always", so a person with no items in a dimension scores 0. "Not assessed" does not occur in the PoC, because the opportunity profile and confidence are out of scope.

**Open questions.** At a selected level of L3 with only one T3 item in Engineering standards: the working tier is taken as T2 (score 0); confirm. Spec 8.3 reads review comments in Coding and code quality only for L2 and L3; decide what the PoC does at a selected level of L1. Whether the score is stored per selected level (three rows) or calculated on request. What exactly counts at the boundaries: "more than 10 points" and "10 to 25". The spec's open "anchor rule" (section 16): one rare T3 item lifts a dimension; keep as written unless decided otherwise. Whether "three developers" needs a minimum number of PRs each.

---

### F12 Profile page

**Goal.** The page management will look at.

**Builds.** One page per developer for the quarter: name, the selected level and a control to change it, the working tier per dimension ("works at T2"), the two dimension scores, the gates panel (G1, G2, and G4 as unverified), the five analytics with charts, and the hygiene number. Every number opens to its evidence items, each item opens to the item page of F08, and from there to GitHub and OpenProject.

**Read first.** `poc-plan.md` 3.1 items 6 and 7. Spec 10.2, 11.2, 11.3.

**Done when.** From the profile page a viewer can click from a dimension score down to a PR on GitHub. Changing the selected level changes the scores and the trail explains why. The numbers on the page equal the API values of F10 and F11.

**Open questions.** Which parts of spec 10.2 and section 11 are shown: the "next step" line (11.3) is cheap and generated from the tier table; the "Work level over time" chart (11.1) is not in scope unless decided. The chart library. The developer list page that leads to a profile.

---

### F13 Accuracy measurement (dropped, decision 80)

**Goal.** A defensible number for how often the AI is right.

**Builds.** A held-out sample through the export and import of F08: 100 Feature work items and 100 review comments, balanced across the AI's complexity values. The 30 items of F08 are the tuning set; 70 are held out. A repeat labeling of 25 items after at least five days. A second AI run on the held-out items. A check of complexity against lines changed. An accuracy page with the measures and targets of `poc-plan.md` section 4, the confusion table and every disagreement with both reasons.

**Read first.** `poc-plan.md` section 4 (all of it) and section 5 risks.

**Done when.** The accuracy page reports each measure of `poc-plan.md` section 4 on the held-out set only.

**Open questions.** Whether it is built at all; you decide after F12. The targets are proposals and are set before labeling starts.

## 6. Order

The features are built in number order; each depends on those in the "Depends on" column. A feature starts when the user asks for it. Two overlaps are allowed to save time (decision 81):

- The deep dive and question brief of the next feature may run in its own session while the current feature builds. Its branch is made from `main` only after the current feature is merged; until then the design reads the current feature's `design.md` for what it will give.
- F11 Scoring may be built in parallel with F07 to F10 once F06 is merged, in its own session and branch. Whichever of two parallel branches merges second renumbers its Flyway migrations above the highest on `main` before it merges.

F05 is the first point where something can be shown. F08 is the first point where the AI can be judged, and the user decides there whether the run of F09 starts. F12 completes the PoC.

## 7. Status

Update this table when a feature changes state. States: not started, in design, design approved, plan approved, in progress, done. Only the user moves a feature to "design approved" or "plan approved". The user's answer to the question brief of `/ship` counts as approval of both the design and the plan, so the feature then goes to "plan approved" (decision 50).

| Id | Feature | State | Design | Plan |
| --- | --- | --- | --- | --- |
| F01 | Repository skeleton | done | `docs/features/F01-repository-skeleton/design.md` | `docs/features/F01-repository-skeleton/plan.md` |
| F02 | GitHub connector | done | `docs/features/F02-github-connector/design.md` | `docs/features/F02-github-connector/plan.md` |
| F03 | OpenProject connector | done | `docs/features/F03-openproject-connector/design.md` | `docs/features/F03-openproject-connector/plan.md` |
| F04 | Identity | done | `docs/features/F04-identity/design.md` | `docs/features/F04-identity/plan.md` |
| F05 | Evidence builders without AI, list page | not started | | |
| F06 | `JudgmentModel`, PR digest, AI-dependent builders | not started | | |
| F07 | Feature work and review comment judgment | not started | | |
| F08 | Blind check and item page | not started | | |
| F09 | Full-window run (one month) | not started | | |
| F10 | Analytics and hygiene | not started | | |
| F11 | Scoring | not started | | |
| F12 | Profile page | not started | | |
| F13 | Accuracy measurement | dropped (decision 80) | | |

## 8. Glossary

The names are fixed by spec section 3. Code, tables and screens use the same names.

| Name | Meaning | Spec |
| --- | --- | --- |
| Data source | A system records are pulled from. Here: GitHub, OpenProject | 3, 5 |
| Raw record | An unchanged copy of one pulled record | PoC |
| Evidence item | One piece of work linked to one person | 3, 6.1 |
| Evidence type | The kind of an evidence item. The spec has 18; the PoC builds 8 | 6.4 |
| Feature work | One person's part of one feature: their tasks and PRs under it | 6.2 |
| Work type | One of 13 labels on a PR or work item: feature, enhancement, fix, maintenance, research, technical task, operational support, refactor, test, docs, infra, performance, security | 6.2 |
| Complexity | Low, Medium, High or Very high, on a Feature work item | 6.2 |
| Tier | T1, T2 or T3 on an item: which level's kind of work it is | 6.3, 6.4 |
| Judgment | One stored answer of the AI or a person about an item, with reason and facts | PoC |
| PR digest | The Stage 1 judgment: a short factual summary of one PR | PoC |
| Analytic | A number or chart calculated from items. Not scored | 7 |
| Quality gate | An analytic that can hold a dimension score down. The PoC applies G1 and G2 | 7.3 |
| Dimension | One row of the DSi Career Framework | 8 |
| Presence | A person has presence at a tier when enough of their items sit at that tier or higher | 9.3 |
| Working tier | The highest tier at which the person has presence. Shown as "works at T2" | 9.3 |
| Dimension score | 0, 1 or 2 for one dimension in one quarter | 9.1 |
| Trail | The stored record behind a dimension score: items, tiers, gate results, notes | 9.1 |
| Hygiene | How well a project keeps its records. Here: the share of PRs linked to a work item | 10.3 |
| Level | L1, L2 or L3. Unknown for this team; the viewer selects one | 3 |
