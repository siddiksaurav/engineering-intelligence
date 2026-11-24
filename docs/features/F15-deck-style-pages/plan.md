# F15 Deck-style pages on existing data: plan

Spec: `docs/features/F15-deck-style-pages/design.md` (approved 24 Nov 2026; decisions 281 to 300).

Backend paths are under `backend/src/main/java/com/dsinnovators/ei/` (tests under `backend/src/test/java/com/dsinnovators/ei/`). Web code follows `web/AGENTS.md` (read the Next.js 16 guides in `web/node_modules/next/dist/docs/` before writing pages) and the F12/F14 patterns: server components, `dynamic = "force-dynamic"`, CSS Modules, tests with `renderToStaticMarkup` and mocked `lib` functions. Charts are the only client components (decision 287). Load the `dataviz` skill before batch 4.

## Batch 1: tasks 1 and 2 (quarter on the developer reads)

### Task 1. Developer list by quarter
`GET /api/developers?quarter=` (required; missing or bad gives 400 through `InvalidQuarterException`). Counts are of current items with `occurred_on` in the quarter. A person is listed only with a current item dated in the quarter or a human PR merged in it (the scoring rule, decision 283); reuse the existing query of that rule rather than copying it.
- Files: `api/DeveloperController.java`, `evidence/CurrentEvidenceItems.java`, `evidence/EvidenceItemRepository.java`; test `api/DeveloperControllerTest.java` (or the existing test of the list).
- **Test first:** `countsOnlyTheItemsOfTheQuarter`: items dated June, July and October; `quarter=2026-Q3` counts July only; a person with only a June item is not listed; no quarter gives 400.
- Commit: `feat(F15): developer list limited to the quarter`

### Task 2. Evidence list by quarter
`GET /api/developers/{id}/evidence-items?quarter=` (required), items with `occurred_on` in the quarter (decision 296).
- Files: `api/DeveloperController.java`, `evidence/EvidenceItemRepository.java`; its test.
- **Test first:** `evidenceItemsOfTheQuarterOnly`: a June and a July item, only July returned; bad quarter 400.
- Commit: `feat(F15): evidence list limited to the quarter`

## Batch 2: tasks 3 to 5 (analytics)

### Task 3. Work complexity without the person
`WorkComplexity.Result.repository` becomes `restOfRepository`: rows of other persons only (decision 284). Javadoc updated.
- Files: `analytics/WorkComplexity.java`, `api/DeveloperAnalyticsResponse.java` if it names the field; `analytics/WorkComplexityTest.java`.
- **Test first:** `restOfRepositoryLeavesThePersonOut`: the person with 2 Medium, another developer with 1 High; person bars 2 Medium, rest of repository 1 High only.
- Commit: `feat(F15): work complexity compares with the rest of the repository`

### Task 4. Work level analytic
New `analytics/WorkLevel.java`, part of `DeveloperAnalytics` and the response as `workLevel`: for `person` and `restOfRepository`, the weighted totals and shares of judged Feature work at T2 or above and at T3, plus `line` (decision 285). Settings `scoring.feature_work_weights`, `scoring.presence_threshold`, `evidence.complexity_tiers` read through the existing readers, never constants.
- Files: `analytics/WorkLevel.java`, `analytics/DeveloperAnalytics.java`, `api/DeveloperAnalyticsResponse.java`; tests `analytics/WorkLevelTest.java`.
- **Test first:** `twoLowAndFiveMediumGiveEightyThreePercentAtT2`: 10/12 at T2 or above, 0 at T3, line one third. Also: unjudged items left out; a changed weight setting changes the share; the person's share equals the Feature work presence of the Coding and code quality trail for the same data (scoring calculator on the same items).
- Commit: `feat(F15): work level share of feature work`

### Task 5. Judged out of total
Every analytic gets `coverage{judged, total}` (decision 286); `ReviewCommentBreakdown` gets `notSubstantive: Count` (decision 297: written = substantive + not substantive + not judged yet).
- Files: `analytics/WorkClassification.java`, `WorkComplexity.java`, `WorkLevel.java`, `ReviewCommentBreakdown.java`, `ReviewParticipation.java`, `ReviewFindingsReceived.java`, `TestingContribution.java`, a small `analytics/Coverage.java`; their tests.
- **Test first:** `ReviewCommentBreakdownTest.countsNotSubstantiveComments`: one substantive, one not substantive, one unjudged; coverage 2 of 3, notSubstantive 1.
- Commit: `feat(F15): judged out of total on every analytic`

## Batch 3: task 6 (team read)

### Task 6. Team analytics
`GET /api/team/analytics?quarter=` (decision 300): the team's work classification (human PRs merged), complexity distribution, hygiene, coverage. Reuses `AnalyticsSql` and the analytics classes without a person filter; no duplicated SQL.
- Files: `api/TeamAnalyticsController.java`, `api/TeamAnalyticsResponse.java`, `analytics/TeamAnalytics.java` (and small changes to share queries); test `api/TeamAnalyticsControllerTest.java`.
- **Test first:** `teamNumbersEqualTheSumOfThePersons`: two developers; team complexity counts equal person counts added; bad quarter 400.
- Commit: `feat(F15): team analytics read`

## Batch 4: tasks 7 and 8 (web clients and chart wrappers)

### Task 7. Web clients follow the API
`fetchDevelopers(baseUrl, quarter)`, evidence list with quarter, `Analytics` type with `restOfRepository`, `workLevel`, `coverage`, `notSubstantive`; new `fetchTeamAnalytics(baseUrl, quarter)`. Callers updated so the build stays green.
- Files: `web/lib/evidence.ts`, `web/lib/profile.ts`, `web/lib/team.ts`, `web/lib/profileParams.ts`, their tests, pages that call them.
- **Test first:** `evidence.test.ts` `fetchDevelopers sends the quarter`.
- Commit: `feat(F15): web clients for quarter, work level and coverage`

### Task 8. Recharts wrappers
Add `recharts` (exact version, latest stable). Client wrappers in `web/components/charts/`: `DonutChart`, `GroupedBarChart` (with optional reference line and label), `StackedBarChart`; colours only from `web/lib/palette.ts`; an accessible text summary under each chart (data as a visually hidden list) so server tests and screen readers read the numbers.
- Files: `web/package.json`, `web/package-lock.json`, `web/components/charts/DonutChart.tsx`, `GroupedBarChart.tsx`, `StackedBarChart.tsx`, `charts.module.css`, `charts.test.tsx`.
- **Test first:** `charts.test.tsx` `DonutChart lists each slice with its count and share`.
- Commit: `feat(F15): chart wrappers on Recharts`

## Batch 5: tasks 9 and 10 (profile)

### Task 9. Profile top half
Header: name, quarter, level switch, the two dimension scores side by side with working tier (decision 293), judged coverage. Section 1 Level of the work: `workLevel` as a grouped bar chart, person against rest of repository, one-third line, one sentence that reads it. Section 2 What kind of work: donut of PRs by work type, three tiles (decision 290), complexity counts next to the rest of the repository (decision 298). Follows `docs/mockups/profile.dc.html` minus the score, growth areas and Planning row.
- Files: `web/app/developers/[personId]/profile/page.tsx`, new section components in that folder, `profile.module.css`, `web/lib/profileText.ts`; tests beside them.
- **Test first:** `profile/page.test.tsx` `shows the two dimension scores in the header`.
- Commit: `feat(F15): profile header, work level and work kind`

### Task 10. Profile lower half
Section 3 Dimensions table (works at, score, why or next step; one greyed "Not assessed" row for the others, one "not rated" row for lead-rated). Section 4 Quality checks (decision 291). Section 5 Reviews: every comment written, judged substantive by tier as a stacked bar, "N not substantive, M not judged yet" (decision 297). Trail and drill-downs folded at the end. Components nothing uses any more are removed.
- Files: profile folder components (`DimensionCard.tsx`, `GatesPanel.tsx`, `GateChart.tsx`, `AnalyticsSection.tsx`, `Bars.tsx`, `Trail.tsx` as needed), tests.
- **Test first:** `shows written comments with not substantive and not judged counts`.
- Commit: `feat(F15): profile dimensions, quality and reviews`

## Batch 6: tasks 11 to 13 (team, overview, compare)

### Task 11. Team list
`/developers?quarter=&level=`: developers of the quarter by name (decision 295), level switch (decision 289), two scores with working tier, items of the quarter as a small stacked bar, a link to compare.
- Files: `web/app/developers/page.tsx`, `developers.module.css`, `page.test.tsx`, `web/lib/team.ts`.
- **Test first:** `lists developers by name with both scores at the chosen level`.
- Commit: `feat(F15): team list for the quarter and level`

### Task 12. Overview
`/?level=`: quarter and level; developers at 0, 1, 2 per dimension (bar); work by type (donut); team complexity (bar); hygiene and judged coverage, from the team read. The "most evidence" ranking is removed (decision 299).
- Files: `web/app/page.tsx`, its CSS and test.
- **Test first:** `shows how many developers score 0, 1 and 2 per dimension`.
- Commit: `feat(F15): overview from the team read`

### Task 13. Compare page
`/developers/compare?a=&b=&quarter=&level=` (decision 294): two columns with the two scores, work level, work type donut, complexity counts; pickers for a and b (links, no client state).
- Files: `web/app/developers/compare/page.tsx`, CSS, test.
- **Test first:** `shows both developers' scores side by side`.
- Commit: `feat(F15): compare two developers`

## Batch 7: tasks 14 and 15 (evidence list, item page, clean-up)

### Task 14. Evidence list and item page
The evidence list takes the quarter (decision 296) and links back to the profile; both pages in the calmer style of the new profile.
- Files: `web/app/developers/[personId]/page.tsx` and its sections, `web/app/items/[itemId]/page.tsx`, CSS, tests.
- **Test first:** `evidence list asks for the quarter of the page`.
- Commit: `feat(F15): evidence list by quarter, calmer item pages`

### Task 15. Remove what nothing uses
`MeterBar`, `SegmentedBar`, `RingMeter`, `TierSteps`, `TypeBar` and their CSS and tests where nothing imports them any more; run the palette check.
- Files: `web/components/**`, `web/lib/palette.test.ts`.
- **Test first:** none new; the palette test runs against the colours the charts use.
- Commit: `refactor(F15): remove the unused CSS chart pieces`
