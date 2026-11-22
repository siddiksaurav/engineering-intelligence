# F14 Interface redesign with charts: notes

## Differences from the plan

- Tasks 1 to 3 (tokens and shell, palette and charts, team numbers) went into one commit and tasks 4 to 7 (the pages) into a second one, instead of one commit per task. The session wrote the code itself (decision 270) and checked each page in the browser as it went; the tests of each task were still written before its code.
- `web/app/developers/evidenceSections.ts` moved to `web/lib/evidenceSections.ts`, because `web/components/` now reads it. Its `colorClass` field is gone; `web/lib/palette.ts` gives the colour of a type.
- `CountBadges.tsx` is removed; the roster draws one bar per developer instead of eight pills.
- The evidence list no longer shows a section for a type with no items ("None"). The summary under the header lists all eight types with their counts, and a type with items links to its section.
- `vitest.config.ts` includes `components/`.
- `.claude/launch.json`: the `web` preview sets `DEFAULT_QUARTER=2026-Q3`, so the overview and the roster show scores in the preview.

## What a later feature should know

- **Shared pieces.** `web/components/Page.tsx` (`PageHeader` with crumbs, title, controls and figures; `PageContent`; `HeaderFigure`), `Panel.tsx` (`Panel`, `Notice`), `Avatar.tsx`, `TierSteps.tsx`, `TierChip.tsx`, `EvidenceTypes.tsx` (`TypeBar`, `TypeLegend`, `Swatch`), `charts/SegmentedBar.tsx`, `charts/RingMeter.tsx`, `charts/MeterBar.tsx`. A new page starts from `PageHeader` and `PageContent`.
- **Colour.** Tokens are in `web/app/globals.css`. A type colour comes from `typeColor`, a tier or complexity colour from `orderedColor`, a score colour from `scoreColor` (`web/lib/palette.ts`). The order of the type colours is what makes neighbours tell apart for colour-blind readers; a new type needs a new run of the palette check (below), not a hand-picked colour.
- **Charts.** Still no chart library (decisions 257, 263). A share that is null has no `role="meter"`; the F12 test for that still holds.
- **Scores on the overview and the roster** are read with one call per developer (decision 266). With many more developers this wants a team endpoint in the backend.
- **F08** adds the source panel and the blind labels to the item page: the page has a two-column body (`item.module.css`, `.columns`) with "AI judgment" left and "Sources" right.
- **Animations.** Bars, rings and steps grow in once on load. A browser tab in the background pauses them at the start, so a screenshot taken by a tool can show empty bars; `document.getAnimations().forEach(a => a.finish())` ends them.

## Verification (23 Nov 2026)

Real data: the Compose database, backend on `main`, quarter 2026-Q3, 30 developers, 26 scored.

**The five pages render in the new design at 1280 px and 375 px.** Looked at in the browser: `/`, `/developers`, `/developers/9/profile`, `/developers/9`, `/items/505`. At 375 px `document.documentElement.scrollWidth` equals `window.innerWidth` (375) on the overview, the roster and the profile: nothing scrolls sideways. Screenshots: `screenshots/` in this folder.

**The numbers on the profile equal the API values.** A script read `GET /api/developers/9/analytics?quarter=2026-Q3` and `GET /api/developers/9/dimension-scores?quarter=2026-Q3&level=L2`, built the 77 texts the page must show (each dimension's name, score and working tier, the person's and the repository's share of each gate, every slice of every distribution with its count and whole percent, the review and testing counts, the hygiene counts) and looked for each in the HTML of `/developers/9/profile?quarter=2026-Q3&level=L2`: none missing. The unit test "every analytics number on the page equals the API value" of F12 is unchanged and green.

**Every number still opens down to a PR.** On `/developers/9/profile?quarter=2026-Q3&level=L3` with all folds open: the trail of "Coding and code quality" lists "Improve status filtering for user administration" (`/items/17`) with `#24120`; the page holds 589 links to pull requests on GitHub. `/items/505` shows the judgment (T2, test kind integration, reason, three facts, model and prompt version) and links to pull request #24072 on GitHub.

**Changing the level changes the scores and the line says why.** Developer 9, Coding and code quality: L2 "2 of 2, works at T3", "Works at T3. L2 expects T2: 2."; L3 "1 of 2", "Works at T3. L3 expects T3: 1." The marker on the tier steps moves from T2 to T3.

**Palette check** (`validate_palette.js` of the `dataviz` skill, light mode, white surface), the eight type colours in the order of `EVIDENCE_SECTIONS`:

```
[PASS] Lightness band         all 8 inside L 0.43–0.77
[PASS] Chroma floor           all 8 >= 0.1
[PASS] CVD separation         worst adjacent #e87ba4↔#12b5d4 ΔE 9.0 (deutan) · tritan 17.1
[PASS] Normal-vision floor
[WARN] Contrast vs surface    below 3:1: #1baf7a, #eda100, #12b5d4, #e87ba4 (relief: every segment is named with its count next to the bar)
```

**Build checks.** In `web/`: `npm run format:check`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`, all green.

## Review

One reviewer read the branch against the design. Four blocking findings, all fixed:

1. An unverified gate with shares (too few developers) drew a chart with zones and a dot. `GateChart` now draws nothing for an unverified gate; test added.
2. to 4. Missing tests: the four header figures of the profile, the tier steps and pips of a dimension card, `expectedTier`, the chart forms of the analytics (segmented bar, bar per row, ring meters). Tests added.

Also fixed from the follow-ups: the muted text colour `--ink-3` is darker (`#536d85`, above 4.5:1 on white); three exports nobody imported are private; an unneeded cast is gone; the design wording now says what was built for Work complexity, the evidence list summary and the item values.

Follow-ups left open:

- The roster and "Most evidence" give the count per type only in the hover title and the label of the bar, next to the visible total; the per-type counts are visible on the evidence list. Showing them in every roster row was the 240 pills of the old page.
- The zones of a gate chart are told apart by colour; the outcome word and the sentence under the chart carry the result.
- The bar of a row sits inside the `<summary>` of its fold, so a screen reader may not announce it as a meter; the count and the percent are text in the same row.
- Some colours are written in the CSS modules and not as tokens (gate zones, the neutral chip, the avatar tints, the warning notice).
- Small duplication: the `DEFAULT_QUARTER` notice and the L2 constant in the overview and the roster; the list of tiers in three files; the percent rounding in `MeterBar`, `RingMeter` and `percent`.
- "Most evidence" has no empty-state text when no developer has an item.
- No tests yet for the roster's column titles, the item page's crumbs and "Sources" card, and the "Profile" link of the evidence list.

## For the user to check

- Add `DEFAULT_QUARTER=2026-Q3` to `.env` (still open from F12). Without it `npm run dev` shows the overview and the roster without scores and says so.
- Open `localhost:3000` and click through: overview, a developer in "Most evidence", the level control, a fold, an item, GitHub.
