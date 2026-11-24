# F15 Deck-style pages on existing data: design

State: draft

## Goal

Pages a manager understands at a glance, shaped like the v1.5 deck (slides 3 to 10 and 28): one idea per section, real donut and bar charts, less on screen. Built only on the data that exists: the two dimension scores of F11 (Coding and code quality; Engineering standards and system health) and the analytics of F10. First the numbers the pages show are fixed, then the pages are rebuilt (decisions 281, 282).

## In scope / Out of scope

In scope:
- Backend fixes: counts limited to the quarter; Work complexity without the person in the project bars; judged out of total on every analytic; the work-level share as an analytic (decision 280); a team analytics read for the overview.
- Web: Recharts charts behind shared wrappers; profile, team list, overview rebuilt; evidence list and item page restyled.

Out of scope (decision 281 and master plan section 4): Planning and execution, any other new dimension, an overall score, growth-area sub-scores, lead-rated scores, a time chart (decision 280), sprint, issue-stage and task counts (no data), gates G3 and G5, new imports, new judgments.

## Data and tables

No new table, no migration, no new setting.

Facts about the data (`ei_latest`, checked 24 Nov 2026): 26 persons with evidence; current items: 379 Review comment written, 109 Test change, 70 Bug fix, 42 Feature work, 25 Documentation, 7 Security, 5 Refactor and performance, 5 Infra and release. Three items fall outside 2026-Q3 (2 Bug fix, 1 Feature work, `occurred_on` June and October), so all-time and quarter counts differ.

Existing interfaces this feature changes (code survey, 24 Nov 2026):
- `GET /api/developers` returns `List<DeveloperSummaryResponse(long personId, String displayName, TypeCounts counts)>`; `TypeCounts` has the longs `featureWork`, `bugFix`, `reviewCommentWritten`, `testChange`, `refactorPerformance`, `security`, `infraRelease`, `documentation`. The counts come from `CurrentEvidenceItems.countsPerPerson()` over every current item, with no date filter (the problem). Every person of `PersonDirectory.all()` is listed, even with no items.
- `GET /api/developers/{id}/evidence-items` reads `findCurrentOfPerson`, all-time.
- `GET /api/developers/{id}/analytics?quarter=` (`DeveloperAnalyticsResponse`): `workClassification: Distribution`, `workComplexity: WorkComplexity.Result{person, repository}`, `reviewParticipation`, `reviewFindingsReceived`, `testingContribution`, `hygiene`. Every count there is already limited to the quarter: PR queries by merge day (`AnalyticsSql`, `merged_pull_request`), items by `occurred_on between :firstDay and :lastDay`.
- `WorkComplexity` adds every row to `repository` and own rows also to `person` (`WorkComplexity.java:66-72`), so the project bars include the person (the problem). Counts are unweighted; bars `low, medium, high, very_high, not_judged_yet`.
- Judged coverage today: `workClassification` and `workComplexity` carry a `not_judged_yet` slice inside the total; `ReviewCommentBreakdown` carries `notJudgedYet` and drops judged non-substantive comments without a count; `testingContribution` reports `withoutDigest` and leaves PRs of other work types out unreported. No analytic says "judged N of M".
- Weights and tiers used by the work-level share: setting `scoring.feature_work_weights` `{"T1":1,"T2":2,"T3":4}`, `scoring.presence_threshold` (one third), `evidence.complexity_tiers` `{"low":"T1","medium":"T2","high":"T3","very_high":"T3"}`. F11's `WorkingTierCalculator` sums judged items only, weighted, `atOrAbove / total`.
- `GET /api/developers/{id}/dimension-scores?quarter=&level=` unchanged.

## Main flows

### Backend fixes (first)

1. **Quarter on the developer list.** `GET /api/developers?quarter=` (required). Counts are of current items with `occurred_on` in the quarter. A person is listed when they have a current item dated in the quarter or a human PR merged in it, the same rule as scoring (`ScoredItems`).
2. **Evidence list by quarter** (Q4 below): `GET /api/developers/{id}/evidence-items?quarter=`.
3. **Work complexity without the person.** `workComplexity.repository` is renamed `restOfRepository` and holds the Feature work items of everyone else. Shares stay.
4. **Work level** (decision 280): a new analytic `workLevel` with, for the person and for the rest of the repository, the weighted share of judged Feature work at T2 or above and at T3, the weighted totals, and the line (`scoring.presence_threshold`). Weights from `scoring.feature_work_weights`, tier from `evidence.complexity_tiers`. Judged items only, as scoring does, so the person's numbers equal the Feature work presence on the Coding and code quality trail.
5. **Judged out of total.** Every analytic gets `coverage{judged, total}`: PRs with a digest of all human PRs (work classification, testing), Feature work items with a complexity (complexity, work level), review comments with a judgment (both comment breakdowns). `ReviewCommentBreakdown` gets `notSubstantive: Count`, so written = substantive + not substantive + not judged yet.
6. **Team analytics** (Q7): `GET /api/team/analytics?quarter=` gives the team's work classification, complexity, hygiene and coverage in one read, for the overview.

### Web (after)

- Recharts behind wrappers in `web/components/charts/`: donut, grouped bar with a reference line, stacked bar. Coloured from `web/lib/palette.ts`. Client components fed with data read on the server; the folds stay without browser JavaScript. CSS chart pieces nothing uses any more are removed.
- **Profile**, top to bottom, after the approved mock-up minus the score, growth areas and the Planning row: header (name, quarter, level switch, judged coverage, headline per Q1); 1 Level of the work: the work-level bar chart with the one-third line and one sentence that reads it; 2 What kind of work: donut of PRs by work type and three tiles (Feature work items closed, PRs merged, bugs fixed); 3 Dimensions: table with works at, score, why or next step; the other dimensions in one greyed "Not assessed" row; 4 Quality checks: G1, G2 pass / close miss / far miss, G4 and the rest "unverified"; 5 Reviews: comments written by tier as a stacked bar; then the trail and drill-downs folded.
- **Team** (`/developers`): one row per developer listed for the quarter, level switch (decision 277), two scores with working tier, items of the quarter as a small stacked bar; order per Q3.
- **Overview** (`/`): quarter and level; how many developers score 0, 1, 2 per dimension (bar); work by type (donut); team complexity (bar); hygiene and judged coverage.
- **Compare** per Q2.
- Evidence list and item page: same content, calmer style.

## Edge cases

- No other developer has Feature work in the quarter: the rest-of-repository bars are empty and say so; the share is null.
- A person with no judged Feature work: work-level shares null, the chart says "no judged feature work".
- Every count 0 for the quarter: the person is not listed on the team page.
- A quarter with no scoring run: scores show "not scored" (as today).
- Very high counts as T3 (setting).
- An unknown or missing `quarter` answers 400.

## How it will be tested

Done when (master plan F15) mapped:
- "Every count is for the selected quarter": a controller test with items in June, July and October; only July counts on `/api/developers?quarter=2026-Q3` and on the evidence list.
- "The project bars leave the person out": `WorkComplexity` test with items of the person and of another developer.
- "Judged out of total": tests per analytic with judged, unjudged and non-substantive items.
- Work level: unit test of the deck example, 2 Low and 5 Medium give (5×2)/(2×1+5×2) = 10/12 = 83% at T2; and a test that the person's share equals the Feature work presence on the Coding and code quality trail of the same data.
- Changing `scoring.feature_work_weights` changes the share without a code change (test).
- Team analytics equal the sum of the per-person numbers (test).
- Web: component tests for each chart wrapper and page section; the palette check; pages on desktop and phone width on a copy of `ei_latest` (prepared for the user); click from a score to a PR on GitHub.
- Backend build and web format check, lint, type check, tests, build green.

## Open points

See the question brief below (written after the answer into "Decisions").

### Question brief

1. Business questions
- Q1. Profile headline without an overall score. A: the two dimension scores side by side, each with "works at". B: a line "dimensions at 2: 1, at 1: 1". C: no headline, start with the work-level chart. Recommended A.
- Q2. Compare view of two developers (deck slides 9, 10). A: build a simple side-by-side page. B: leave it out. Recommended A.
- Q3. Team list order. A: by name. B: by Coding and code quality score, then Engineering standards. Recommended A.
- Q4. Evidence list of a developer. A: limited to the quarter, like every other page. B: all-time, each item labelled with its quarter. Recommended A.
- Q5. "Review comments written" number. A: every comment written, the tier chart of judged substantive ones beside it with "N not substantive, M not judged". B: only substantive ones. Recommended A.
- Q6. Feature work charts on the profile. A: only the work-level chart (weighted shares, one-third line). B: also the complexity counts next to the rest of the repository. Recommended A.
- Q8. The overview's "most evidence" ranking. A: drop it. B: keep it, for the quarter. Recommended A.

2. Technical decisions
- Q7. Team numbers for the overview. A: one new read `GET /api/team/analytics?quarter=`. B: the web adds up the per-developer analytics (26 calls). Recommended A.

3. Decisions made by the session: 283 to 292.
