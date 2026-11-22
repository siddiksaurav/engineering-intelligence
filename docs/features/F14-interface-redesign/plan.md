# F14 Interface redesign with charts: plan

Spec: `docs/features/F14-interface-redesign/design.md` (approved 23 Nov 2026, decisions 261 to 270).

Web code follows `web/AGENTS.md` and the F05 and F12 patterns: server components, `dynamic = "force-dynamic"`, CSS Modules, tests with `renderToStaticMarkup` and mocked `lib` functions. The `frontend-design` and `dataviz` skills are loaded before the first component.

## Batch 1: tasks 1 to 3 (foundation)

### Task 1. Tokens, fonts and the shell
The tokens of the design in `globals.css`; the three `@fontsource` packages; `AppBar` with the current section marked; `PageHeader`, `Panel`, `Notice`, `StatTile`, `Avatar`.
- Files: `web/package.json`, `web/app/globals.css`, `web/app/layout.tsx`, `web/components/*`.
- **Test first:** `AppBar.test.tsx` "marks the link of the current section with aria-current"; `Avatar.test.tsx` "shows the first two letters of a name in upper case".
- Commit: `feat(F14): design tokens, fonts and the shared shell`

### Task 2. Palette and chart components
`web/lib/palette.ts` (colour of a type, tier, complexity, score; replaces `colorOf` of `Bars.tsx`); `SegmentedBar`, `RingMeter`, `MeterBar`, `TierSteps`, `TierChip`.
- Files: `web/lib/palette.ts`, `palette.test.ts`, `web/components/charts/*`, `web/components/TierSteps.tsx`, `TierChip.tsx`.
- **Test first:** `SegmentedBar.test.tsx` "gives each segment the share of the total as its width and draws nothing for a total of 0"; `RingMeter.test.tsx` "is a meter with the whole percent and says no PRs without a meter for a null share"; `TierSteps.test.tsx` "fills the steps up to the working tier and marks the expected one".
- Commit: `feat(F14): palette and chart components`

### Task 3. Team numbers
`web/lib/team.ts`: `fetchTeamScores(baseUrl, personIds, quarter, level)` and `teamSummary(developers, scores)` (totals per type, score and working-tier counts per dimension, the developers with the most evidence).
- Files: `web/lib/team.ts`, `team.test.ts`.
- **Test first:** `team.test.ts` "counts the developers at each score per dimension and leaves a failed call as not scored".
- Commit: `feat(F14): team scores and summary for the overview and the roster`

## Batch 2: tasks 4 to 7 (pages)

### Task 4. Overview dashboard at `/`
- Files: `web/app/page.tsx`, `page.test.tsx`, `overview.module.css`.
- **Test first:** `page.test.tsx` "shows the stat tiles, evidence by type and the scores of the team"; also no quarter, backend down.
- Commit: `feat(F14): overview dashboard`

### Task 5. Developer roster
- Files: `web/app/developers/page.tsx`, `page.test.tsx`, `developers.module.css`; `CountBadges.tsx` is removed.
- **Test first:** `page.test.tsx` "shows each developer's two scores and working tier, and not scored without them".
- Commit: `feat(F14): developer roster with scores`

### Task 6. Profile with charts
Header band with the level control and four figures; dimension cards with the score figure and tier steps; a bullet chart per gate; the analytics as rings, segmented bars and ranked rows.
- Files: `web/app/developers/[personId]/profile/*`.
- **Test first:** `page.test.tsx` "draws a gate as a chart with the person's and the repository's share"; the existing assertions stay.
- Commit: `feat(F14): profile with score figures, gate charts and analytics charts`

### Task 7. Evidence list and item page
- Files: `web/app/developers/[personId]/*`, `web/app/items/[itemId]/*`.
- **Test first:** `page.test.tsx` of the evidence list "shows the counts per type in the header and links each to its section".
- Commit: `feat(F14): evidence list and item page in the design system`

## Verification

`cd web && npm run format:check && npm run lint && npm run typecheck && npm test && npm run build`, then the browser checks of the design ("How it will be tested") at 1280 px and 375 px.
