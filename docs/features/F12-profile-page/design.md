# F12 Profile page: design

State: draft

## Goal

One web page per developer and quarter, the page management will look at: the two dimension scores against a level the viewer selects, the working tier of each dimension, the gates, the five analytics as charts and the hygiene number. Every number opens to its evidence items, each item opens to a read-only item page with the AI's judgment, and from there to GitHub and OpenProject. It completes the PoC (master plan section 6).

## In scope / Out of scope

**In** (subject to the question brief, "Open points").
- The profile page in `web/`: header, level control, two dimension cards with the "why" line, the trail, the gates panel, the five analytics drawn as bars, the hygiene number.
- The drill-down: every count opens to its items and PRs.
- A minimal read-only item page (Q1), and the backend reads it needs: item summaries by id and one item with its current judgment.
- Links from the developer list and the evidence list to the profile.

**Out** (master plan section 4 and the F12 block).
- The weighted overall score (spec 10.1), the six growth-area bars, confidence, the opportunity section, lead-rated rows, allocation (spec 10.2 rows that rest on out-of-scope parts).
- Gates G3 and G5; G4 is shown only as "gate unverified".
- The screen where a lead corrects a judgment.
- The source panel (work item text, PR descriptions, files) and the human label of the F08 item page: F08 adds them later (decision 178).
- The staffing, hygiene and best-practice reports (spec 10.3), the EM's list by level (spec 10.4), the consistency check (spec 11.4).
- Authentication. Any scheduler. No new table, no migration.
- "Work level over time" (spec 11.1, 11.2) unless Q6 decides otherwise.

## Data and tables

No migration. F12 reads; it writes nothing.

### Rules copied from the spec and the plan

**Spec 10.2 The profile** (the sections, and what the PoC does with each):

| Section of the profile (spec) | What it shows (spec) | PoC |
| --- | --- | --- |
| Header | Name, level, quarter, projects and allocation, the score, confidence, and the hygiene % of the person's projects | Name, quarter, the selected level and its control, the repository's hygiene %. No overall score, confidence or allocation (out of scope) |
| Work level over time | The main chart (section 11) | Q6 |
| Six growth-area bars | Sub-score per growth area | Not shown: two dimensions only, no growth areas |
| Strengths and gaps | Dimensions that scored 2. Dimensions that scored 0, each with the tier the work sat at, the gate that failed if any, and the items. Every dimension has a "next step" line | Q8, Q5 |
| Dashboard analytics | All analytics from Step 3. The quality gates in their own panel, each as pass, close miss or far miss | The five analytics and hygiene; gates panel with G1, G2, and G4 as unverified |
| Opportunity, Lead-rated rows | | Not shown (out of scope) |
| Trail | Every dimension score opens to its items, their tiers, the gate results, and any correction a lead made with its reason | Q9. No corrections exist |

**Spec 11.3 A "next step" line under every dimension score.** "Under each dimension score the profile shows what work at the next tier would look like. The line is generated from the tier table in 6.4 and is never written by hand." Examples: "Design and architecture: 1, works at T2. To score 2: a Design task with options compared, or an ADR." "Planning and execution: 1, works at T2. To score 2: a third of weighted feature work at High."

**Spec 6.4 The tier table**, the rows of the six tiered types of the two dimensions (T4 is not used in the PoC, decision 171):

| Evidence type | T1 | T2 | T3 |
| --- | --- | --- | --- |
| Feature work | Low complexity | Medium complexity | High complexity |
| Review comment written | A question or a small catch | Logic, edge case or readability | Design, security or performance |
| Test change | Unit test in own PR | Integration or regression test | A test gate in CI for a module |
| Refactor and performance item | Cleanup in own PR | Small refactor or speed-up on own initiative | Planned debt item, or a dependency kept current, or a measured bottleneck removed |
| Security item | Fix to a reported vulnerability in own code | Dependency fix or access-control fix | Security check added to the team pipeline |
| Infra and release item | Not used | Pipeline fix, Dockerfile, logging, or cutting a release (tag, branch, hotfix) | Team pipeline or monitoring |

**Score against a selected level** (spec 9.1 step 5, decision 204): working tier lower than the level's tier (L1 = T1, L2 = T2, L3 = T3) or none gives 0, the same gives 1, higher gives 2. Gates (decision 211): any far miss gives 0, otherwise any close miss turns a 2 into 1. At L3 a 2 needs T4, which the PoC never gives: the best score at L3 is 1.

**Presence** (spec 9.3, decisions 203, 207): frequent types (Feature work, Review comment written, Test change) have presence at a tier when the weight (Feature work: T1 1, T2 2, T3 4) or count at that tier or higher is at least one third of the total; rare types when at least one item sits at that tier or higher. The working tier is the highest tier with presence in any type of the dimension. At L3, Engineering standards also needs two T3 items (decision 220).

### What F12 reads (exact shapes, from F10 and F11)

`GET /api/developers` (F05): `[{personId, displayName, counts}]`.

`GET /api/developers/{personId}/evidence-items` (F05, F06): `{person:{personId, displayName}, items:[{id, type, date, quarter, title, sourceUrl, links:[{label,url}], tier:{tier, reason}|null, fields}]}`. `tier` is only the `item_tier` judgment.

`GET /api/developers/{personId}/analytics?quarter=2026-Q3` (F10, decisions 186 to 200). 404 unknown person, 400 bad quarter.
- `Count = {count, evidenceItemIds: long[], pullRequestNumbers: int[]}`; `Slice = {name, count: Count, share: number|null}`; `Distribution = {total, slices: Slice[]}`. Shares are unrounded, null when the total is 0.
- `person`, `quarter`, `workClassification: Distribution` (by digest work type, plus `not_judged_yet`), `workComplexity: {person: Distribution, repository: Distribution}` (slices `low`, `medium`, `high`, `very_high`, `not_judged_yet`), `reviewParticipation: {pullRequestsReviewed: Count, authorsReviewed: Count, comments: ReviewCommentBreakdown, substantivePerPullRequestReviewed}`, `reviewFindingsReceived: {pullRequests: Count, comments: ReviewCommentBreakdown, substantivePerPullRequest}`, `testingContribution: {featureAndFixPullRequests: Share, withoutDigest: Count, bugFixPullRequests: Share}`, `hygiene: {total, linked: Count, notLinked: Count, share}`.
- `ReviewCommentBreakdown = {substantive: Count, byCommentType: Distribution, byTier: Distribution, notJudgedYet: Count}`; `Share = {total, tested: Count, untested: Count, share, testedByTier: Distribution}`.

`GET /api/developers/{personId}/dimension-scores?quarter=2026-Q3&level=L2` (F11, decisions 217, 228, 229, 231, 232). 404 unknown person, 400 bad quarter or level.
- `{person, quarter, level, scored, jobRunId, calculatedAt, dimensions: [{key, name, workingTier: "T1"|"T2"|"T3"|null, score: 0|1|2, gates, notes: string[], trail}]}`; `scored: false` with no dimensions before a run.
- `gates` and `trail.gates`: `[{gate: "G1"|"G2"|"G4", outcome: "PASS"|"CLOSE_MISS"|"FAR_MISS"|"UNVERIFIED", unverifiedReason: "TOO_FEW_DEVELOPERS"|"NO_PULL_REQUEST_OF_THE_KIND"|"NO_REPOSITORY_PULL_REQUESTS"|"NOT_CALCULATED"|null, personTestedPullRequests, personPullRequests, repositoryTestedPullRequests, repositoryPullRequests: int[], personSharePercent, repositorySharePercent, differencePoints: number|null, developersInGroup: long[], minimumDevelopers, limits: {passWithinPoints, farMissBeyondPoints}|null, pullRequestsWithoutDigest: int[], note}]`. Gates sit on Engineering standards only.
- `trail = {dimensionKey, level, evidenceTypesVersion, gatesVersion, settings, evidenceTypes: [{evidenceType, rule: "FREQUENT"|"RARE", counted, notJudgedYet, notSubstantive, notReadAtLevel: TrailItem[], presence: [{tier, atOrAbove, total, presence}]}], levelThreeRule, workingTier, scoreBeforeGates, gates, countedNotTiered: TrailItem[], notes, finalScore}`.
- `TrailItem = {evidenceItemId, itemKey, evidenceType, tier, complexity, weight, pullRequestNumbers: int[], sourceUrl}`.

### Real data (checked 22 Nov 2026 on the Compose database)

- 30 persons. Current evidence items: 2026-Q3 holds 529 (379 Review comment written, 64 Bug fix, 38 Test change, 36 Feature work, 8 Documentation, 4 Security); 2026-Q2 holds 2 Bug fix, 2026-Q4 holds 1 Feature work. The most items of one person: 96 (person 1).
- Judgments are far from complete: 60 PR digests of 172 human PRs (run 57 left 145 for later), 7 Feature work complexities (30 items wait for digests), 41 review comment judgments (338 left for later), 42 item tiers. Most numbers on a profile today are "not judged yet".
- The Compose database is at migration V11: the running backend is from before F10, so it has no analytics or scores endpoints and no `dimension_score` table. Verification needs a backend on `main` (see "How it will be tested").
- A `feature_work_complexity` value (run 59): `{workType, complexity, rubric, facts:[{fact, quote, source:"PR-23972"|"WI-75000"}], reason, judgedOnPartialInput, leftOutPullRequests}`; the `facts` column holds `[{fact, link}]` with full GitHub and OpenProject links. A `review_comment` value: `{commentType, substantive, reason}`. An `item_tier` value: `{kind}`.

## Main flows

Routes (Q2, Q12): the profile at `/developers/{personId}/profile?quarter=2026-Q3&level=L2`, the item page at `/items/{itemId}`.

1. **Open.** The page reads `quarter` and `level` from the URL; a missing one takes its default (Q3, Q4); a wrong one gives the not-found page (D237). On the server it calls, in parallel, the analytics and dimension-scores endpoints, then the item summaries of every item id on the page in one call (Q12, Q13).
2. **Header.** Name, quarter, the level control (three links L1, L2, L3, the selected one marked), the repository's hygiene share, a "not judged yet" notice when any count holds unjudged items (Q10), links to the evidence list and the developer list.
3. **Dimension cards**, one per dimension: name, score (0, 1, 2), "works at T2" (or "no working tier"), the why line generated from working tier, level, score before gates and gate notes (D236), e.g. "Works at T2. L3 expects T3: 0." or "Works at T2, L1 expects T1: 2, held to 1 by G1 (close miss)." Under it the next-step line (Q5) and the strength or gap badge (Q8). The trail folds out (Q9).
4. **Gates panel.** G1 and G2 from the Engineering standards dimension: outcome, the person's share against the repository's, the difference in points, the limits, the group size; each count opens to its PR numbers. G4: "gate unverified". An unverified gate states its reason in words.
5. **Analytics.** Each drawn as horizontal bars (Q14): Work classification (share per work type), Work complexity (person and repository side by side, low to very high and not judged yet), Review participation (PRs reviewed, authors reviewed, substantive comments per PR reviewed, mix by comment type, by tier, not judged yet), Review findings received (per PR, by type, by tier), Testing contribution (feature and fix PRs tested, Bug fix PRs tested, tested by tier, PRs without digest), Hygiene (linked share of the repository).
6. **Drill-down.** Every count opens (Q12) to its items (title, type, tier where known, link to the item page) and its PR numbers (each a link to `https://github.com/opf/openproject/pull/{n}`).
7. **Item page** (Q1). Title, type, date, the person, the links to GitHub and OpenProject, and the current AI judgment of the item's kind: tier, the value in words (complexity, comment type and substantive, test kind), reason, the facts each with its link, model, prompt version, judgment date. Bug fix and Documentation: "counted, not tiered". No judgment yet: "not judged yet". A row that is no longer current says so and links to the current row (D238).

**Uses.** The three endpoints above; `web/lib/evidence.ts` (`read`, `EvidenceResult`); the F05 page layout and colours (`developers.module.css`, `globals.css`, decision 110). Backend: `CurrentEvidenceItems`, `EvidenceItemRepository`, `PersonDirectory`, `JudgmentRepository.findCurrentOfEvidenceItems(JudgmentKind, Collection<Long>)`, `EvidenceItemResponse.of`.

**Gives.** The profile route and the item route; F08 extends the item page with the source panel and the human label. Backend (Q13 A): `GET /api/evidence-items?ids=` and `GET /api/evidence-items/{id}`.

## Edge cases

- No `dimension-scoring` run for the quarter (`scored: false`): the cards say "No scores for 2026-Q3 yet" and how they are made; the analytics are still shown (D239).
- Person unknown: the not-found page. Backend down: "backend down", as F05.
- A share that is null (total 0): "no PRs" in place of a percent, and no bar.
- An item id from a trail that a later `evidence-build` superseded: the summary and the item page still show the row and say it is no longer current (D238).
- A count of 0: shown, with nothing to open.
- Many items under one count (379 review comments in the repository, up to 96 per person): the list is folded until opened (Q12).
- A Review comment written with no substantive judgment, Feature work with Very high: shown as "Very high (counts as T3)".
- An item id that does not exist in the batch call: left out and logged; the count still shows (the API is the source of the number).
- A level of L3: the best score is 1; the next-step line says T3 is the top tier in the PoC.

## How it will be tested

Master plan "Done when", made exact:
1. **From a dimension score down to a PR on GitHub.** Web test: the profile renders a dimension card whose trail item links to `/items/{id}`, and the item page renders a link to `https://github.com/opf/openproject/pull/{n}`. Browser check on real data (built-in browser): click score → trail → item → GitHub link, screenshot.
2. **Changing the level changes the scores and the trail explains why.** Web test: the page called with `level=L1` and `level=L3` shows the scores the mocked API gives and the why line naming the working tier and the level. Browser check: switch L1, L2, L3 on one developer.
3. **Numbers equal the API values of F10 and F11.** Web tests: every number in the rendered markup equals the mocked API value (counts, rounded shares). Real data: a script reads both endpoints for one developer and compares them with the numbers the page shows (text of the page), recorded in `notes.md`.

Also: backend tests for the two item endpoints (unit and database tests: current and superseded rows, judgment of each kind, not judged yet, unknown id 404, bad ids 400, too many ids 400); web tests for `lib` clients (timeouts, 404, down), the why line and next-step generators, share rounding, the drill-down lists; architecture tests stay green (no new package).

Real data needs the Compose database at V12 with a `dimension-scoring` run: a backend on `main` is started only when no job runs (`CLAUDE.md`); otherwise the check runs on a throwaway copy of the database, as F11 did.

## Open points

Question brief, shown to the user on 22 Nov 2026.

### 1. Business questions

**Q1. F08's item page is deferred. What does an item open to?**
- A. A small read-only item page now: title, type, person, links to GitHub and OpenProject, and the AI's judgment (tier, complexity or comment type, reason, facts with links, model, prompt version). F08 later adds the source text and the human label.
- B. Straight to GitHub or OpenProject; the AI's reason is never shown.
- C. The full F08 item page now, with the source panel (work item text, PR descriptions, files).
- Recommended: A. The master plan's chain score → item → judgment → source works, at small cost.

**Q2. Where does the profile live?**
- A. A new page `/developers/{id}/profile`; the developer list links to it; it links to the full evidence list.
- B. The profile replaces `/developers/{id}`; the evidence list moves to `/developers/{id}/evidence`.
- Recommended: A. No F05 page moves; one click from the list either way.

**Q3. Which quarter does the page show by default?**
- A. 2026-Q3, set in `.env` as `DEFAULT_QUARTER`; the URL can change it.
- B. The newest quarter that has a scoring run (none yet in the Compose database).
- C. Today's quarter (2026-Q4: empty).
- Recommended: A. Explicit, works before any scoring run.

**Q4. Which level is selected when the page opens?**
- A. L2.
- B. L1.
- C. None: the page asks the viewer to pick one first.
- Recommended: A. The middle level shows both a higher and a lower comparison.

**Q5. Show a "next step" line under each score (spec 11.3)?**
- A. Yes, generated from the tier table: what work at the needed tier looks like for each type of the dimension, e.g. "To score 2: a third of weighted Feature work at High, or a design, security or performance review comment".
- B. No.
- Recommended: A. Cheap, and it turns a score into an action.

**Q6. The "Work level over time" chart (spec 11.1)?**
- A. Not built: the data covers two weeks, so the chart would have one point.
- B. Build it.
- Recommended: A. One point is not a trend.

**Q7. The developer list that leads to a profile?**
- A. Keep F05's list; each name opens the profile.
- B. Also show each developer's two scores at L2 (a new endpoint for all persons).
- C. A list per level sorted by score (spec 10.4).
- Recommended: A. The PoC shows one profile; ranking people is a later decision.

**Q8. Strengths and gaps (spec 10.2)?**
- A. A badge on each dimension card: "strength" at 2, "gap" at 0 with the tier the work sat at, the gate that failed and the items. No separate section.
- B. A separate section, as in the spec.
- C. Nothing.
- Recommended: A. With two dimensions a separate section repeats the cards.

**Q9. How much of the trail is shown?**
- A. All of it, folded: per evidence type the presence table (at or above, total, one third, yes or no), the items with tier and weight, items not judged yet, not substantive or not read at the level, the L3 two-T3 rule, gate numbers, notes and stand-ins.
- B. Only the items and the score line.
- Recommended: A. "Can be recalculated by hand" needs the sums.

**Q10. Most items are not judged yet (22 Nov: 60 of 172 PR digests, 7 of 37 Feature work, 41 of 379 comments). How is that shown?**
- A. A notice at the top ("N items not judged yet; numbers will change") and the "not judged yet" bar in each analytic.
- B. Only the bar in each analytic.
- Recommended: A. A manager must not read a half-judged page as final.

**Q11. Do the repository numbers (project bar of Work complexity, hygiene, gate shares) open too?**
- A. Yes, to other developers' items and PRs, like the person's numbers.
- B. No, only the person's numbers open.
- Recommended: A. "Every number opens" (master plan); the comparison must be checkable.

### 2. Technical decisions I need your direction on

**Q12. How does a number open?**
- A. Folds open in place under the number (no page change, works on a phone, no browser JavaScript).
- B. A separate drill-down page per number.
- Recommended: A. Fewer clicks, the context stays in view.

**Q13. Where do item titles and judgments come from?**
- A. Two new read endpoints: `GET /api/evidence-items?ids=` (titles, links, person, current or not) and `GET /api/evidence-items/{id}` (with the current judgment).
- B. No new endpoint: the person's own evidence list gives titles; other people's items show as "item #id"; no item page (only with Q1 B).
- Recommended: A. Needed by Q1 A and Q11 A; one call per page.

**Q14. Chart library?**
- A. None: bars drawn with CSS Modules, rendered on the server, every bar clickable (decision 110: no new dependency).
- B. Recharts: richer charts, but browser JavaScript and a new dependency.
- Recommended: A. The charts are bars and shares; CSS draws them.

### 3. Decisions I made myself (overrule any by number)

- D233. The page calls the F10 and F11 endpoints unchanged, on the server, as F05 does.
- D234. Shares shown as whole percents; a null share shows "no PRs".
- D235. Header: name, quarter, level control, repository hygiene. No overall score, confidence or allocation (out of scope).
- D236. The "why" line is generated from working tier, level, score before gates and gate notes.
- D237. A wrong quarter or level in the URL gives the not-found page.
- D238. An item superseded since the scoring run is still shown, marked "no longer current" with a link to the current row.
- D239. Before a scoring run the page shows "No scores for this quarter yet" and still shows the analytics.
- D240. The gates panel reads G1, G2 and G4 from the Engineering standards dimension.
- D241. The judgment kind of an item: Feature work → complexity, Review comment written → review comment, Test change, Refactor, Security, Infra → item tier; Bug fix and Documentation → "counted, not tiered".
- D242. The next-step text (if Q5 A) is a typed copy of spec 6.4's six rows in `web/`; it is wording, not a number.
- D243. Colours stay bold sky blue with the F05 type colours; no purple.
