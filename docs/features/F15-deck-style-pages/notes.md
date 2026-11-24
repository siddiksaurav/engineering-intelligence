# F15 Deck-style pages on existing data: notes

## Differences from the plan

- Tasks 1 and 2 are one commit (`e1c8f92`): both change the same controller, repository and test file. The implementer wrote test and code in one pass, without a separate red run.
- `QuarterParameter.parse` is a new shared parser in `api`; it replaces the private copies in the analytics and dimension-scores controllers. `CurrentEvidenceItems.countsPerPerson()` is removed; the developer list reuses `ScoredItems.of(quarter)`.
- Between batch 1 and batch 4 the web calls `/api/developers` and the evidence list without a quarter and gets 400; batch 4 fixes the clients.
- Batch 2: no separate red run for tasks 3 and 4 (tests did not compile before the types existed).
- `WorkLevel` takes each item's tier from its current complexity judgment (set from `evidence.complexity_tiers` at judging time), as scoring does; it does not read that setting again. Weights and the line come from `ScoringSettingsReader.in(quarter)`. This keeps the person's share equal to the Coding and code quality trail.
- `analytics.WorkLevel` imports `scoring.ScoringSettingsReader` while `scoring.ScoredItems` imports `analytics`: a package cycle no architecture test forbids (left for the review).
- `workClassification` changes shape to `{distribution, coverage}`. The review analytics carry coverage on their comment breakdown (`comments.coverage`), not a field of their own.
- Batch 3: `GET /api/team/analytics?quarter=` gives `quarter`, `workClassification{distribution, coverage}`, `workComplexity{distribution, coverage}` (`WorkComplexity.TeamResult`), `hygiene`. "Team" is every person with a human PR merged in the quarter (classification) and every Feature work item (complexity), so the team equals the sum of the persons; hygiene still counts PRs of authors without a person row. The per-person and team reads share one SQL template. No separate red run.
- Batch 4: recharts 3.10.1 (3.11.x was under two weeks old). Wrappers `DonutChart`, `GroupedBarChart`, `StackedBarChart` with `ChartFrame` (hidden text summary of the numbers) and `chartFormat.ts`; chart chrome colours `CHART_*` in `palette.ts`. Overview, developer list and evidence list show a notice and call nothing when `DEFAULT_QUARTER` is unset; the optional-score branches are gone. `fetchEvidenceItems(baseUrl, personId, quarter)`.
- Batch 5: the profile follows the mock-up, sections numbered 1 to 5 (the score section is gone). It no longer shows Review findings received, Testing contribution or Hygiene as panels (the mock-up has none; testing shows through G1 and G2, hygiene moves to the overview). The strength/gap badges, tier-step pips and the gate bullet chart are replaced by the table's score pill and tier chip and a text line with a status pill. Removed: `AnalyticsSection`, `Bars`, `DimensionCard`, `GatesPanel`, `GateChart`, `badgeOf`, `expectedTier`. New: `lib/counts.ts`, `workLevelSentence`, `plainName`, `workTypeColor`, `PERSON_COLOR`, `COMPARISON_COLOR` (grey comparison; the dataviz check passes colour-blind separation and warns on chroma by intent).
- Batch 6: shared `components/LevelSwitch.tsx` and `components/ScoreLine.tsx`. The team list's small bar is the existing `TypeBar`. `TierSteps`, `MeterBar`, `RingMeter` removed here (not in task 15); `teamSummary`, `mostEvidence`, `byWorkingTier` replaced by `dimensionSummaries`. The compare page (`page.tsx`, `Pickers.tsx`, `Sections.tsx`, `compareHref.ts`) puts both developers on one axis for work level and complexity rather than two columns. Its test fixtures cast partial answers to the full types.
- Batch 7: quarter and level travel through every link between the pages; the item page links with the item's quarter (level falls back to L2). Task 15 found nothing more to remove (`TypeBar`, `TypeLegend`, `Swatch`, `SegmentedBar` are still used); only the unused `size` option of `SegmentedBar`/`TypeBar` went.

## Review

One review round. Blocking, fixed: the "Bugs fixed" tile counted PRs the model labelled `fix` instead of Bug fix items (new analytic `bugsFixed: Count`, the person's current Bug fix items of the quarter); Planning and execution was missing from the "Not assessed" row. Follow-ups fixed in the same round: `PersonDirectory.all()` dead code removed; one person read for the developer list; one `percent` rounding rule in the charts; percent axes with ticks at 0, 25, 50, 75, 100%; a test for a person listed only through a merged PR; shared `lib/workTypeSlices.ts` and `lib/people.ts`; the compare sections renamed `ComparedWorkLevelSection`, `ComparedWorkKindSection`.

## Follow-ups

- `analytics/WorkLevel.java` imports `scoring` while `scoring/ScoredItems.java` imports `analytics` (a package cycle), and `WorkLevel` repeats the weighted at-or-above sum of `scoring/WorkingTierCalculator.java`; only `WorkLevelTest.theShareEqualsTheFeatureWorkPresenceOnTheCodingTrail` keeps them in step. Move `WorkLevel` into `scoring` or share one weighting function, and add an architecture test for the package direction.
- `profile/DimensionsSection.tsx` writes the not-assessed dimension names and `LEAD_RATED_COUNT = 13` as constants, and the header writes "of 2"; better read from the `dimension` table.
- The coverage of Review findings received and Testing contribution is computed but no page shows it since the profile dropped those panels (batch 5). Decide with the user whether they come back.

## Verification (24 Nov 2026, on a copy of `ei_latest`)

- Backend `./mvnw verify`: 1,034 tests, 0 failures. Web: format check, lint, type check, 251 tests, build green.
- Every count of `/api/developers?quarter=2026-Q3` equals a SQL count of current items dated in Q3, for all 26 developers (the 3 items outside Q3 are not counted).
- For every developer, person plus rest of repository equals the team complexity, and the team complexity (23 Low, 16 Medium, 2 High; 41 of 41 judged) equals the sum of the persons.
- For every developer, `workLevel.person` equals the Feature work presence on the Coding and code quality trail at L2.
- For every developer, `bugsFixed` equals `counts.bugFix` of the team list.
- Team coverage: 172 of 172 human PRs digested, 41 of 41 Feature work items judged.
- `/`, `/?level=L3`, `/developers`, `/developers/9`, `/developers/9/profile`, `?level=L1`, `/developers/compare?a=9&b=10`, `/items/1` answer 200 on the production build; the profile links to PRs on github.com.
- Waiting for the user: look at the pages on desktop and phone width (`localhost:3000`, `/developers`, `/developers/9/profile`, `/developers/compare?a=9&b=10`) with the backend on `ei_latest`; the palette check passed in the tests.

## What a later feature should know

- `GET /api/developers` and `GET /api/developers/{id}/evidence-items` need `?quarter=`. `GET /api/team/analytics?quarter=` gives the team's numbers.
- Analytics shapes: `workClassification{distribution, coverage}`, `workComplexity{person, restOfRepository, coverage}`, `workLevel{person, restOfRepository, line, coverage}`, `bugsFixed`, comment breakdowns with `notSubstantive` and `coverage`.
- Charts: `web/components/charts/` `DonutChart`, `GroupedBarChart`, `StackedBarChart` (Recharts 3.10.1, client components with a hidden text summary); `LevelSwitch`, `ScoreLine` shared.
