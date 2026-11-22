# F10 Five analytics and the hygiene number: design

State: approved on 2026-11-22, through the question brief ("OK": every recommendation)

## Goal

An API that returns, for one developer and one quarter, the five analytics of `poc-plan.md` 3.1 item 5 and the repository's hygiene number. Nothing is stored and nothing is scored. F12's profile page reads it; F11 calculates its own gate shares.

## In scope / Out of scope

**In.**
- A new package `analytics`: one read-only SQL query per analytic over `evidence_item`, `judgment` and, where a count is about pull requests rather than items, the stored GitHub records (`raw_record`) and `pull_request_work_item`.
- The five analytics: Work classification, Work complexity, Review participation with Review effectiveness, Review findings received, Testing contribution. The hygiene number: share of PRs linked to a work item, for the repository.
- One HTTP endpoint in `api` (shape: Q12).
- Enabling `SpecNumbersTest.theAnalyticsPackageHoldsNoNumberConstant` (disabled "F10 creates the analytics package").

**Out.**
- Any screen (F12). The web is not changed.
- Scoring, gates G1 and G2 (F11). G1 is a different number from Testing contribution (master plan F10 block).
- The other analytics of spec 7.2 (Completed work, Delivery outcome, Issues on own work, ...): left out on purpose, `poc-plan.md` 3.1.
- DSi-wide comparison by level in Work complexity: levels are unknown for this team (`poc-plan.md` section 1); only the "what the project offered" bar is built.
- F08 and F09 are deferred (decision 178): F10 runs on the two-week window, 1 to 14 Jul 2026.

## Data and tables

No migration, no new table, no new setting. Read only.

### Rules copied from the spec and the plan

Spec 7 (opening): "Analytics are **not** scored. ... Every count on the dashboard is split by tier, so a busy number also shows at which level the work was."

Spec 7.2 (the rows F10 builds):

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Work classification | What kind of work the person spent the quarter on | A pie chart of closed items by work type. For example 60% feature, 20% fix, 10% refactor, 10% docs |
| Work complexity | How hard the person's work was | A bar chart of the person's feature work by Low, Medium and High. Next to it are two comparison bars: the middle value for the same level across DSi, and what the project offered |
| Review findings received | How many real problems reviewers find in the person's code before it is merged | Substantive review comments received per PR, by type. Style comments are left out |
| Testing contribution | Whether the person writes tests together with their code | The share of feature and fix PRs that include test changes, or coverage of changed lines where CI reports it. Also the share of bug fixes that add a regression test |
| Review participation | How much the person reviews other people's code | The number of PRs reviewed and the number of different authors reviewed. It is always shown next to Review effectiveness |
| Review effectiveness | Whether the person's reviews find real problems | Substantive comments per PR reviewed, and the mix of comment types (correctness, security, performance, design, maintainability). An approval with no substantive comment counts as zero |

Spec 10.3: "The hygiene report goes to each project. It shows the share of PRs linked to a work item ... Poor hygiene is reported on the project. It is never read as poor performance of a person."

Master plan F10 table: Work classification "closed items by work type, as shares"; Work complexity "the person's Feature work by Low, Medium, High, next to the same for the project"; Testing contribution "share of feature and fix PRs that include test changes; share of Bug fix PRs that change tests"; Hygiene "share of PRs linked to a work item, for the repository". "Every count is split by tier (spec 7)."

`poc-plan.md` 3.4: "Analytics are SQL queries over `evidence_item` and `judgment`; they are not stored."

### What F10 reads (from earlier features)

- `evidence_item` (V8, V9): `id`, `item_key`, `evidence_type` (`feature_work`, `bug_fix`, `review_comment_written`, `test_change`, `refactor_performance`, `security`, `infra_release`, `documentation`), `person_id`, `occurred_on`, `fields` jsonb, `withdrawn`, `supersedes_id`. Current item: no row supersedes it and it is not withdrawn (F05 notes).
  - Review comment written `fields`: `comment.{url,path,isReply,createdAt,databaseId}`, `pullRequest.{url,title,number,mergedAt,externalId}`, `pullRequestAuthorPersonId`. Dated by the PR's merge day (decision 121).
  - Feature work dated by the first done day of its Feature (decision 112); Bug fix by the Bug's first done day (decision 114); items built from one PR by the merge day (decision 150).
- `evidence_item_source` (`evidence_item_id`, `raw_record_id`, `role` in `pull_request`, `pull_request_diff`, `work_package`, `review_comment`): which PRs an item rests on.
- `judgment` (V9, V11): `kind` in `pull_request_digest`, `item_tier`, `feature_work_complexity`, `review_comment`; subject `raw_record_id` or `evidence_item_id`; `value` jsonb; `tier` T1 to T4; `supersedes_id`. Current judgment: no row supersedes it.
  - `pull_request_digest` on the PR record version: `value.workType` is one of the 13 work types (feature, enhancement, fix, maintenance, research, technical_task, operational_support, refactor, test, docs, infra, performance, security).
  - `feature_work_complexity` on the item row: `value.complexity` in `low`, `medium`, `high`, `very_high`; `tier` from `evidence.complexity_tiers` (very_high gives T3, decision 171).
  - `review_comment` on the item row: `value.commentType` (ten types), `value.substantive`; `tier` null when not substantive (decision 170). F10 reads substantive from the value (F07 notes).
  - `item_tier` on Test change, Refactor and performance, Security, Infra and release items (decision 154).
- `raw_record` kind `pull_request` (latest `version` per `external_id`): `payload.author.{login,__typename,databaseId}`, `payload.mergedAt`. Kind `review`: `payload.{state,author,pullRequest,submittedAt}`.
- `person_identity` (GitHub `external_id` = account `databaseId`, decision 82) maps an account to a person.
- `pull_request_work_item` (`pull_request_external_id`, `state` `resolved` or `not_visible`): a link counts only when `resolved` (decision 104).
- Settings `evidence.review_comment_types` (substantive types) and `github.bot_logins` (bots, with GraphQL `__typename` `Bot`, decision 84).
- `common.Quarter` (`parse`, `firstDay`, `lastDay`).

### Real data (checked 22 Nov 2026 on the Compose database)

- 215 stored PRs, 172 by humans, all merged 1 to 14 Jul 2026 (2026-Q3). 133 of the 172 have a resolved work item link: hygiene 77.3% (poc-plan section 1 measured about 78%).
- 674 stored reviews: 168 APPROVED, 35 CHANGES_REQUESTED, 471 COMMENTED.
- Current items: 35 Feature work, 62 Bug fix, 379 Review comment written. Digests are being written in the background (decision 178): 5 of 172 at the time of writing, so most Stage 1 and Stage 2 judgments do not exist yet.

## Main flows

`GET /api/developers/{personId}/analytics?quarter=2026-Q3` (shape and drill-down: Q11, Q12). 404 for an unknown person (as decision 109), 400 for a missing or wrong quarter. Each analytic below is one query; the answer is counts, totals and shares (count divided by total, not rounded, decision 186); a share with a total of 0 is null.

1. **Work classification** (Q1, Q9, Q10). The person's human-authored PRs merged in the quarter, counted by the work type of their current digest. A PR without a digest counts under "not judged yet" so the shares add up to 100%.
2. **Work complexity** (Q6, Q7, Q8, Q10). The person's current Feature work items dated in the quarter, counted by the complexity of their current `feature_work_complexity` judgment; the same for every current Feature work item in the quarter (the repository stands in for the project). Complexity gives the tier, so the split by complexity is the split by tier.
3. **Review participation and Review effectiveness** (Q4). PRs reviewed in the quarter (by merge day), the number of different authors, substantive comments (current Review comment written items of the person whose current `review_comment` judgment says substantive) per PR reviewed, the mix by comment type, and the substantive comments by tier.
4. **Review findings received.** On the person's human-authored PRs merged in the quarter: substantive review comments received (`fields.pullRequestAuthorPersonId` = person), per PR (divided by the number of those PRs, also those with no comment), by comment type and by tier. Style and acknowledgement are not substantive and are left out.
5. **Testing contribution** (Q2, Q3). Of the person's PRs merged in the quarter whose digest work type is in the feature-and-fix set, the share with a current Test change item; and of the PRs of the person's current Bug fix items in the quarter, the share with a current Test change item. Tested PRs split by the Test change item's tier.
6. **Hygiene** (Q5). Of the repository's human-authored PRs merged in the quarter, the share with at least one resolved work item link.

**Uses.** Tables above; `common.Quarter`; `identity.PersonDirectory.find(personId)` for the 404; `setting.SettingService` for `evidence.review_comment_types` and `github.bot_logins`.

**Gives** (to F12, F11): the endpoint above and the `analytics` service it calls (class names fixed in the plan).

## Edge cases

- A person with no PR, no review and no item in the quarter: every count 0, every share null.
- A PR whose digest is missing or failed: "not judged yet" in Work classification; left out of the feature-and-fix set of Testing contribution and counted as "PRs without digest" there.
- A Feature work item or review comment without a current Stage 2 judgment: "not judged yet" bucket (Q10).
- A superseded or withdrawn item, a superseded judgment: never counted.
- A PR stored in several versions: the latest version is the PR; its digest is the current digest of any of its versions' records (as `PullRequestDigests.currentOf`).
- A PR that links several Features is in several Feature work groups (decision 116): each item counts once in Work complexity; the PR counts once in Work classification.
- A review comment on a bot's PR counts for the reviewer (decision 111).
- A bot PR and a PR with no author (deleted account) count nowhere for a person; whether they count in hygiene is Q5.

## How it will be tested

Backend tests extend `DatabaseTest`, build their rows directly and never call a model.

- One test class per analytic, each with a small hand-made data set: the counts, the shares, the tier split, the "not judged yet" bucket, a superseded item and a superseded judgment that are not counted, another person's rows that are not counted, rows outside the quarter that are not counted.
- Work classification shares add up to 1 (master plan "Done when" 2).
- A PR with no evidence item shows in Work classification ("Done when" 3).
- Controller test: 200 with the shape, 404 unknown person, 400 bad quarter.
- `SpecNumbersTest.theAnalyticsPackageHoldsNoNumberConstant` enabled and green.
- "Done when" 1, real data: for one developer with judged items, the session runs separate SQL from the evidence list for each number and shows both to the user (`notes.md`, "Verification"). It runs on whatever the background model jobs have judged by then.

## Decisions

The user answered "OK" to the question brief on 22 Nov 2026: every recommended option.

- Q1 to Q12 → decisions 189 to 200: Work classification per PR by digest work type (189); feature-and-fix set is feature, enhancement, fix (190); any test change counts for a Bug fix PR (191); any review or inline comment makes a PR reviewed (192); hygiene on human PRs (193); four complexity bars (194); project bar is the whole repository, person included (195); complexity and comment types shown now, replacing decision 175 for F10 to F12 (196); no tier split in Work classification (197); a "not judged yet" bucket (198); every count carries its item ids and PR numbers (199); one endpoint `GET /api/developers/{personId}/analytics?quarter=` (200).
- Made by the session: decisions 179 to 188 (package and SQL, quarter rule, current rows, Test change item decides "includes tests", both Bug fix kinds, authors are persons, per-PR denominator, unrounded shares, required quarter, no web change).
