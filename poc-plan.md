# Engineering Intelligence PoC: plan

This is the design document of the PoC. The feature list and build status are in `docs/master-plan.md`. The spec it refers to is `docs/spec/spec-v1.5.md`. A build step in section 3.7 is the feature with the same number in the master plan (build step 2 is F02, build step 11 is F11).

## Context

The spec (Version 1.5) puts all judgment in one step: the AI sets work type, complexity and tier on each evidence item (sections 6.2 to 6.4). Everything after that is counting. The PoC must show management that this judgment is correct often enough to trust, and make that visible. It uses public data (github.com/opf/openproject and community.openproject.org), and it must be the first slice of the real system, not throwaway code.

Decisions already made with you: scope option B now (option C later), no review screen in the PoC, the accuracy measurement is optional and comes after all planned features are built, the demo is planned separately, real developer names are shown, any cloud LLM, stack is PostgreSQL + Spring Boot + Next.js.

Decisions added after the plan review on 17 Nov 2026:
- Code written with AI belongs to the person who pushed it.
- A bug is never Feature work.
- The second dimension is Engineering standards and system health, not Planning and execution.
- A 30-item blind check is added at build step 8.

## 1. What the real data contains

Checked on 16 Nov 2026 against both live APIs.

**GitHub (opf/openproject)**
- About 100 human-merged PRs per week from about 36 authors (316 in the last 3 weeks, plus 92 bot PRs from dependabot and the CI account).
- 78% of human-merged PRs link to a work item, almost always as a URL in the PR body (`community.openproject.org/wp/COMMS-1078`). Branch names add very little.
- PR bodies are rich ("What are you trying to accomplish", "What approach did you choose and why").
- Reviews and inline review comments are available with file path and diff hunk. Many comments come from the Copilot review bot, and many are the author's own replies. Both must be separated from human reviewer comments.
- PRs carry labels for AI usage ("ai: Collaborative", "ai: Directed", "ai: Autonomous"). The label is ignored. The PR belongs to its author and is judged like any other.
- Large features are shipped as stacked PRs (for example five PRs "1/5" to "5/5" on one Feature).

**OpenProject (community.openproject.org/api/v3, no login)**
- 1,753 work items updated in the last 30 days. Types: Bug, Feature, Implementation, Epic, Code maintenance, Documentation, Task, Design Task.
- Hierarchy exists: Epic > Feature > Implementation / Task / Bug. About half of items have a parent.
- Full status history per item, with who and when. Statuses include "review failed" and "test failed", which can stand in for reopens.
- Each work item lists its GitHub PRs, so the link works from both sides.
- Custom fields "Developers" and "Module" exist. 83% of items have a real description.
- Work items have two ids: a number (81224) and a project key (COMMS-1078). The key changes when an item moves project (SE-694 and COMMS-1078 are the same item).

**Missing or weak, compared with what the spec expects**

| Spec expects | Real data |
| --- | --- |
| Assignee identifies the person | Assignee is cleared at each hand-over; only 21% of items have one. Use the PR author, the "Developers" field and the status history |
| Bug severity | Not present. Only priority (91% are "Medium") |
| "Found in" stage on bugs | Not present. "Observed in versions" is filled on 47% of bugs |
| Sprint moves with a reason | Not present. Versions are releases |
| Estimates, story points | 6% and 9% of items |
| Person's level (L1 to L3), allocation | Not present (external team) |
| Release date | Partly: the PR state "deployed" on the work item |

Consequence: gates G3, G4 and G5, the clean-delivery rule and the tier of a bug fix cannot be shown fairly on this data. Work type, complexity, tier, review comment judgment, and gates G1 and G2 can.

## 2. Scope options

| Option | Contains | Proves | Effort |
| --- | --- | --- | --- |
| A. Judgment only | Ingest, AI judges Feature work, accuracy report | The AI can judge work type and complexity | About 1 week |
| **B. Judgment + profile slice (chosen)** | A, plus Review comment judgment, tiers for Test change, Refactor, Security and Infra items, 5 analytics, gates G1 and G2, 2 dimension scores, one profile page | The judgment is correct and it carries through to a score a person can trace back to a PR | About 3 to 4 weeks |
| C. Wider slice (later) | B, plus tiers for Bug fix and Documentation, the Planning and execution and Knowledge sharing dimensions | Breadth across dimensions | About 5 to 6 weeks |

B is the smallest option where management sees the whole chain: source record, evidence item, tier, analytic, gate, dimension score, on two dimensions that rest on different evidence.

## 3. The recommended option in detail

### 3.1 Features

1. **Collect** PRs (with files, reviews, review comments) and work items (with history, parent, linked PRs) for one quarter, Q3 2026 (1 Jul to 30 Sep), plus one month on each side. In addition, for every feature that reached a done status in the quarter, all its linked PRs are pulled whatever their date, so long features are complete.
2. **Build evidence items** (rules in 3.2).
   - With a tier: Feature work, Review comment written, Test change, Refactor and performance item, Security item, Infra and release item.
   - Captured and counted only, with a work type and no tier: Bug fix, Documentation.
3. **AI judgment** in two stages (see 3.2): work type and complexity for Feature work, tier derived from complexity; comment type and tier for review comments; test kind and tier for the other tiered types. Each judgment stores its reason, the facts it rests on, and source links.
4. **Item page** (read-only): one evidence item with its source on the left (work item text, PR descriptions, files changed, links to GitHub and OpenProject) and the AI's work type, complexity, tier, reason and facts on the right.
5. **Five analytics**: Work classification, Work complexity (person against project), Review participation with Review effectiveness, Review findings received, Testing contribution. One project hygiene number is shown with them: the share of PRs linked to a work item (spec 10.3).
6. **Two dimension scores**, chosen so they rest on different evidence.
   - **Coding and code quality**: Feature work and Review comment written. No gates.
   - **Engineering standards and system health**: Test change, Refactor and performance item, Security item, Infra and release item. Gates G1 and G2 are applied (see 3.3); G4 is shown as "gate unverified". Bug fix items are listed on the trail as "counted, not tiered".
   - Both use the presence rule and weights 1, 2, 4 from spec 9.3. A Very high item counts as T3 with weight 4 and is shown as "Very high" on the item page.
   - Because levels are unknown for this team, the profile shows the working tier ("works at T2") and the dimension score under a level the viewer selects. At a selected level of L3, Engineering standards needs two T3 items (spec 9.3).
7. **Profile page** for one developer with the trail: every number opens to its items, each item to its judgment and source record.

Left out on purpose: gates G3 and G5, the clean-delivery rule, the Planning and execution dimension, confidence by allocation, opportunity profile, the weighted overall score, lead-rated dimensions, authentication, and the screen where a lead corrects a judgment (spec 9.7 row 1). The data model already supports corrections, so that screen can be added later without changes.

### 3.2 How evidence items are built and judged

**The AI's two stages**

- **Stage 1, PR digest.** One call per PR. Input: PR title, body, file list, and the diff with generated files, lock files and vendored code removed. Output: work type of the PR, modules touched, schema or data migration yes/no, new integration yes/no, nature of the change, tests included yes/no, test kind (unit, integration, regression, CI gate), a short reason. For the test change in a PR, and for a PR whose work type is refactor, performance, security or infra, it also gives a tier from the rows of spec 6.4, with a reason. A cheaper model is enough here.
- **Stage 2, Feature work judgment.** One call per Feature work item with a strong model. Input: the work item texts (feature and the person's child items), priority, linked items, and the PR digests. Output: work type, complexity (Low, Medium, High, Very high), the rubric facts from spec 6.2 with a quote or link for each, a reason, and a confidence value. Lines of code are not given to the model.
- **Tier** is not asked from the model for Feature work. It is read from the complexity by the table in 6.2.
- **Review comments.** One call per PR for all human reviewer comments, each with its diff hunk. Output per comment: type, substantive or style, tier (6.4 row 2), reason. Bot comments and the author's own replies are filtered out before the call.
- Every judgment stores model name, prompt version and a hash of the input, so it can be reproduced.
- The model sits behind one Java interface (`JudgmentModel`). Start with a strong Claude model through the Anthropic API; check the current Java SDK or Spring AI support and model ids when building.

The only hand-written rules: the building rules below, the bot filter, the file filter for diffs, and the complexity-to-tier table.

**Building rules.** The person on every item is the PR author.

| Evidence item | Built from | Tier |
| --- | --- | --- |
| Feature work | For each merged PR, follow its work item up to the nearest ancestor of type Feature (or the item itself if there is none). One item = one PR author + one such feature + all that author's PRs and child items under it. Work items of type Bug are left out of the group | From complexity |
| Feature work, marked "no work item" | A PR with no work item whose work type is feature or enhancement | From complexity |
| Bug fix | A work item of type Bug with the PRs that fixed it, even when it sits under a Feature. Also a PR with no work item whose work type is fix | None. Counted only |
| Test change | One item per PR that changes tests | From test kind: unit in own PR is T1, integration or regression is T2, a test gate in CI for a module is T3 |
| Refactor and performance item, Security item, Infra and release item | A PR with that work type whose work item is not under a Feature (for example type "Code maintenance", or no work item). A PR under a Feature stays in that Feature work group | From spec 6.4 |
| Documentation | A PR with no work item whose work type is docs | None. Counted only |
| No evidence item | A PR with no work item whose work type is maintenance, technical task, research or operational support. The spec has no evidence type for these. The PR keeps its work type and shows in Work classification | None |

- A bug is never Feature work. The tracker type decides this, not the AI.
- A Feature work item counts in the quarter when the feature reaches a done status (merged, tested, closed; kept as a setting).
- Every PR with no work item also counts in the hygiene number.

### 3.3 Gates G1 and G2

Both follow spec 7.3 and 9.1 step 6: pass within 10 points of the comparison group, close miss at 10 to 25 points (a 2 becomes 1), far miss beyond 25 points (the score becomes 0). Fewer than three developers in the group gives "gate unverified".

| Gate | Person's number | Compared with |
| --- | --- | --- |
| G1 Tests with features | Share of the person's PRs with work type feature or enhancement that change tests | The same share for all developers in the repository in the quarter |
| G2 Regression tests | Share of the person's Bug fix PRs that change tests | The same share for all developers in the repository in the quarter |

Two stand-ins are used and shown on the trail. "Changes tests" stands in for "adds a regression test" in G2. The repository stands in for the spec's "project", because a PR with no work item has no OpenProject project.

### 3.4 Data model (PostgreSQL)

| Table | Purpose |
| --- | --- |
| `data_source` | GitHub, OpenProject. Later: CI/CD and others |
| `raw_record` | Unchanged copy of each pulled record (PR, review comment, work item, activity) as JSONB, with external id, URL, content hash. Never edited |
| `person`, `person_identity` | One person, many outside identities (GitHub login, OpenProject user id). `level` is nullable |
| `project` | OpenProject project |
| `evidence_item` | Common fields of spec 6.1: person, project, date, source link, evidence type, marker. Type-specific fields of 6.5 in a JSONB column. `supersedes_id` for corrections; rows are never updated |
| `evidence_item_source` | Which raw records an item was built from (its tasks and PRs) |
| `evidence_item_link` | Related items |
| `judgment` | One row per judgment: subject (evidence item or raw PR), kind (work type, complexity, comment type, test kind, tier), value, tier, reason, facts with source links, confidence, judged by (AI or person), model, prompt version, input hash, `supersedes_id`. Append-only. The current tier of an item is its latest judgment |
| `human_label` | Your blind labels from build step 8, and later from step 13: item, work type, complexity, labeled at |
| `setting` | Presence threshold, weights, done statuses, gate limits, with a version per quarter (spec 14) |
| `dimension`, `dimension_evidence_type` | The mapping of spec 8.3, versioned |
| `dimension_score` | Person, dimension, quarter, level used, working tier, score, gate results and trail as JSONB |

Analytics are SQL queries over `evidence_item` and `judgment`; they are not stored.

One change from the spec: the tier lives in `judgment`, not on the item. This gives "the AI's original tier is kept next to the lead's" (6.3) and reproducibility without extra work.

### 3.5 Architecture

One Spring Boot application with packages that match the spec's steps, plus a Next.js front end.

```
source/      connectors: github, openproject  -> raw_record        (Step 1)
evidence/    builders per evidence type        -> evidence_item     (Step 2)
judgment/    JudgmentModel interface, prompts, judgment store       (Step 2)
analytics/   queries per analytic                                   (Step 3)
scoring/     mapping, presence, working tier, gates, score          (Steps 4, 5)
review/      blind-label export and import                          (PoC check)
api/         REST for the UI
web/         Next.js: item page, profile page
```

Adding a data source later means a new connector. Adding an evidence type means a new builder, a prompt and a row in the mapping. Nothing else changes.

### 3.6 Tech stack

- Java 25, Spring Boot 4.x, Maven, Spring Data JDBC or JPA, Flyway migrations, PostgreSQL (Docker Compose). Exact versions are fixed at the start of feature F01 and recorded in `docs/decisions.md`.
- GitHub GraphQL API with a read-only token (the unauthenticated limit of 60 requests per hour is too low). OpenProject API v3 without login.
- LLM through the `JudgmentModel` interface, JSON-schema structured output.
- Next.js (App Router, TypeScript), a chart library for the pie and bars.
- Jobs are started by REST endpoints or a command-line runner; no scheduler yet.

### 3.7 Build order

1. Repository skeleton: Spring Boot app, Flyway schema for `data_source`, `raw_record`, `person`, `project`; Docker Compose; Next.js shell.
2. GitHub connector: merged PRs with files, reviews, review comments into `raw_record`. Run on two weeks first.
3. OpenProject connector: work items referenced by those PRs, their ancestors, children, activities; and for each feature done in the window, all its linked PRs whatever their date.
4. Identity: create persons from PR authors; propose the OpenProject user from the "Developers" field.
5. Builders that need no AI: Feature work for PRs with a work item, Bug fix from the tracker type, Review comment written. A first web page lists the evidence items per developer with links to GitHub and OpenProject. This is the first visible result. Check 20 groups by eye, and check that no Bug item sits in a Feature work group.
6. Stage 1 PR digest with the `JudgmentModel` interface, including test kind and the tiers for test, refactor, security and infra. Measure cost and time on 50 PRs. Then the builders that need the PR's work type: Test change, Refactor and performance, Security, Infra and release, Documentation, and the items for PRs with no work item.
7. Stage 2 Feature work judgment and review comment judgment, stored in `judgment`.
8. Blind check, then item page.
   - The system exports 30 Feature work items as a CSV with the item id and the links to GitHub and OpenProject, without the AI's answer. You fill in work type and complexity, and the file is imported into `human_label`.
   - Only then the item page opens: the list from step 5 shows each item with the AI's judgment and reason, and your label next to it where you gave one. The page reports how often the two agree.
   - You read further items by eye, and the prompts are adjusted from what you find. These 30 items are the tuning set of section 4.
9. Extend ingest and judgment to the full quarter.
10. Five analytics, the hygiene number, and their API.
11. Scoring: settings, mapping, presence, working tier, gates G1 and G2, two dimension scores with trail.
12. Profile page.

Optional, after step 12:

13. Accuracy measurement (section 4): a larger held-out sample through the same export and import as step 8, and an accuracy page. Nothing in steps 1 to 12 depends on it, and it needs no change to the existing tables.

## 4. How we measure whether the AI judged correctly (optional, after the planned features)

This section is deferred. It describes build step 13. Until then, the checks on the AI are the 30-item blind check in build step 8 and your own reading of items on the item page.

- **Sample.** 100 Feature work items and 100 review comments from the quarter, picked at random but balanced across the AI's complexity values so High is not rare in the sample. The 30 Feature work items from step 8 are the tuning set; 70 are held out and never used to change a prompt.
- **Blind labeling, in a sheet.** The system exports the sample as a CSV with the item id and the links to GitHub and OpenProject, and without the AI's answer. You fill in work type and complexity for each row and the file is imported back. Only after that do you open the items to see the AI's answer, and mark for each whether its reason is factually right. This catches a correct answer given for a wrong reason.
- **Your own consistency.** With one labeler we cannot tell AI error from opinion. So after at least five days you label 25 of the same items again, blind. Your agreement with yourself is the fair ceiling for the AI.
- **AI stability.** Run the AI twice on the held-out items and report how often it gives the same answer.
- **Size check.** Compare the AI's complexity with lines changed. A strong link would mean it is judging size, which the spec forbids.

Numbers reported, on the held-out set only:

| Measure | Proposed target |
| --- | --- |
| Work type: AI equals your label | 85% or more |
| Complexity: exact match | 70% or more, and close to your own repeat agreement |
| Complexity: within one step | 95% or more |
| Off by two steps (Low judged High or the reverse) | 2% or less |
| Reason factually right | 90% or more |
| Review comment tier: exact match | 75% or more |
| Same answer on a second run | 90% or more |

The accuracy page also shows the confusion table and lists every disagreement with both reasons. The targets are proposals; set them before labeling starts.

## 5. Risks and proposed spec changes

**Risks**
- **Scores built on a lightly measured judgment.** Before step 13, the only number for how often the AI is right comes from 30 items, and those items are also used to adjust the prompts. If step 13 later shows poor accuracy, the prompts change and the scores are recalculated; the code does not change.
- **Tiers outside Feature work are not checked.** The blind check covers work type and complexity of Feature work. The tiers of test, refactor, security and infra items are checked only by eye.
- **One labeler.** The accuracy number rests on one person's view. The repeat-labeling check reduces this; a second labeler on 30 items would reduce it much more.
- **Different kind of team.** OpenProject is a senior product team. Most work may land at Medium and High, so Low is under-tested. DSi client projects will differ. The PoC shows the method works, not what DSi's numbers will be.
- **Tier depends on the career framework.** The rubric in 6.2 is used as written. The framework statements at careerpath.innovatorslab.net should be added to the prompts before a DSi pilot.
- **No levels.** Dimension scores are shown for a selected level. They illustrate the mechanism only.
- **Gates use stand-ins.** G1 and G2 compare against the whole repository, and G2 counts any test change as a regression test (see 3.3).
- **Very large PRs.** Diffs may exceed what one call can read. The file filter and the two-stage design handle most; the rest are marked "judged on partial code".
- **Cost.** About 2,200 PRs for the five months collected, and several hundred Feature work items. Measured in build step 6 before the full run.

**Changes I propose to the spec**
1. **Person on a Feature work item (5, 6.2).** Do not rely on the assignee. Use the PR author; for tasks without a PR, the assignee at the moment work started, read from the status history.
2. **Two-stage judgment (6.2).** Add the PR digest step. It keeps large features readable for the model and gives the lead a short factual summary per PR.
3. **Tier follows complexity (6.3, 6.4).** For Feature work the AI judges complexity only, and the tier is read from the table. Two separate AI answers could contradict each other.
4. **Judgment as its own record (5, 6.1).** Store tier, reason, model and prompt version in a judgment record, not on the item.
5. **Module count in the complexity table (6.2).** "Three or more modules is High" is a size rule and conflicts with "size is not complexity" in the same section. Keep module count as one recorded fact among several, not as a deciding rule. A rename across five modules is Low.
6. **Review comments (6.4, 7.2).** State that bot comments and the author's replies in their own PR are not Review comment written items.
7. **Work type (6.2).** A Feature work item needs one work type of its own, separate from the work types of its PRs. The tracker's own type (Bug, Feature, Code maintenance) is given to the AI as input.
8. **"Feature closes" (6.2).** Define it as a list of done statuses per project, kept as a setting.
9. **Bugs and Feature work (6.2).** State that a work item of type Bug is always a Bug fix item and is never grouped into Feature work, even under a Feature.
10. **AI-written code (6.2).** State that code written with AI belongs to the person who pushed it and is judged like any other.
11. **PRs with no work item (5, 6.2).** Say what such a PR becomes, including one whose work type has no evidence type (maintenance, technical task, research, operational support).
12. **Refactor, security and infra PRs under a Feature (6.2, 8.6).** Say whether such a PR is part of the Feature work group, its own evidence item, or both.
13. **Weight of Very high (9.3).** The weights stop at T3 = 4. Say how a Very high item counts until T4 is used.
14. **New open point (16).** Whether DSi's OpenProject has bug severity or only priority.

## Verification

- Connectors: row counts in `raw_record` match the GitHub and OpenProject totals for the window; the share of PRs linked to a work item is about 78%.
- Builders: 20 Feature work items checked by hand against GitHub and OpenProject (right person, right PRs, right feature). No item whose tracker type is Bug appears as Feature work.
- Judgment: every `judgment` row has a reason, at least one source link, a model and a prompt version; a second run with the same input hash is skipped.
- Blind check: the exported CSV contains no AI answer.
- Scoring: unit tests reproduce the worked example in spec 9.3 (2 Low + 5 Medium gives working tier T2) and section 12.
- Gates: unit tests for G1 and G2. Within 10 points passes, 10 to 25 holds a 2 at 1, beyond 25 gives 0, fewer than three developers gives "gate unverified".
- End to end: from the profile page, click a dimension score down to a PR on GitHub.
