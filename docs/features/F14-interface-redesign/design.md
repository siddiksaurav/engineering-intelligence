# F14 Interface redesign with charts: design

State: approved on 2026-11-23 by the user's instruction to build it without a question brief (decision 261)

## Goal

The web pages look and read like a product that can be shown to a director. One design system, a team overview dashboard, and charts where the pages had rows of text. No number changes and no backend change.

## Review of the pages before F14

Looked at on 23 Nov 2026 in the browser at 1280 px and 375 px, with the real data of the Compose database (30 developers, quarter 2026-Q3).

1. **No shell.** Each page has its own gradient header and a "back" link. There is no navigation, no product name on the inner pages, no page title in the browser tab besides the product name.
2. **The home page is three lines of unstyled text.** It is the first page a viewer opens.
3. **One 720 px column on every screen.** On a desktop two thirds of the screen are empty and the profile is eleven screens long.
4. **The scores are the point of the profile and are the smallest thing on it.** A dimension card is a title, a 1.8 rem digit and three paragraphs. The working tier, which the whole model turns on, is four words of grey text.
5. **The gates are paragraphs.** A gate compares two shares against two limits; the page says it in a sentence and lists five folds.
6. **The "charts" are one 10 px bar per row,** each on its own line under a fold, with no legend and no part-to-whole view. Work complexity of person and repository are two lists side by side that the eye cannot compare.
7. **The developer list is 30 cards of 8 pills each,** 240 pills, most of them "0". It does not show a score, so it cannot answer "who should I look at".
8. **Colour.** Eight saturated type colours are used as fills behind white text (amber and cyan fail contrast). Every card has a 2 px border and an 8 px coloured left border. The palette was never checked for colour-blind readers; the red and the pink of two neighbouring types cannot be told apart.
9. **Type.** The system font at two sizes. Numbers are not set apart from prose.
10. **Details.** Default fold markers, default underlined links, no hover or focus styling, notices that look like cards.

## In scope / Out of scope

**In.**
- Design tokens (colour, type, spacing, radius, shadow) in `web/app/globals.css`; three self-hosted typefaces.
- A shared shell: the app bar with the product name and the navigation (Overview, Developers), and a page header component.
- `/` becomes the overview dashboard.
- `/developers` becomes a roster with the two dimension scores and the working tier of each developer.
- `/developers/{id}/profile`: score and working-tier figure per dimension, a chart per gate, charts for the five analytics and the hygiene number.
- `/developers/{id}` (evidence list) and `/items/{id}` in the same system.
- Phone width (375 px) for every page.

**Out.**
- Any change to a number, to the backend or to the database.
- Everything in "Out of scope" of the master plan: no overall score, no confidence, no opportunity profile, no "Work level over time" chart (decision 249).
- A dark theme. The pages declare `color-scheme: light`. It can be added on the tokens later.
- Sorting or filtering in the browser. The pages stay server-rendered; a number still opens in place with `<details>` (decision 255).

## Data and tables

No table, no endpoint, no setting. The pages read what F05, F10, F11 and F12 give:

- `GET /api/developers` → `Developer[]` (`personId`, `displayName`, `counts` per evidence type). `web/lib/evidence.ts`, `fetchDevelopers`.
- `GET /api/developers/{id}/evidence-items` → `DeveloperEvidence`. `fetchEvidenceItems`.
- `GET /api/developers/{id}/analytics?quarter=` → `Analytics`. `web/lib/profile.ts`, `fetchAnalytics`.
- `GET /api/developers/{id}/dimension-scores?quarter=&level=` → `DimensionScores` (`scored`, `dimensions[]` with `key`, `name`, `score` 0 to 2, `workingTier`, `gates`, `trail`). `fetchDimensionScores`.
- `GET /api/evidence-items?ids=` and `/{id}`. `web/lib/items.ts`.

New in F14: the overview and the roster ask the dimension scores of every developer at L2, one call per developer in parallel (decision 266). Checked on 23 Nov 2026: one call takes 7 ms and answers 15 to 33 kB; 30 developers.

Facts about the real data (23 Nov 2026): 30 developers; 6 of them have no evidence item; `myabc` has 96 items, the median is about 17. Work classification names are the 13 PR work types of spec 6.2 plus `not_judged_yet`; comment types are ten. So neither can have a colour per class.

## Design system

**Subject.** An instrument that reads engineering work. The pages borrow from graph paper and from the tier steps of the spec, not from a generic admin template.

**Colour** (the user wants vivid colour and sky or azure blue, no purple).

| Token | Value | Use |
| --- | --- | --- |
| `--navy-950`, `--navy-900`, `--navy-800` | `#041527`, `#071f38`, `#0b2f52` | App bar and page header band |
| `--azure-700`, `--azure-600`, `--azure-500` | `#0b5cba`, `#0f6fde`, `#1d8bf5` | Links, the selected state, the one-hue bars |
| `--sky-400`, `--sky-300`, `--sky-100`, `--sky-50` | `#38bdf8`, `#7dd3fc`, `#e0f2fe`, `#f0f8ff` | Highlights on navy, tracks, washes |
| `--sun` | `#ffb703` | The one warm accent: the "expected" marker of a level |
| `--page`, `--surface`, `--line` | `#eef5fb`, `#ffffff`, `#d6e4f0` | Page plane, cards, hairlines |
| `--ink`, `--ink-2`, `--ink-3` | `#0a2540`, `#3d5a75`, `#6b859c` | Text: primary, secondary, muted |

Evidence types are identities and keep one colour each on every page. The order is the order of `EVIDENCE_SECTIONS`, which is the order of the segments in a bar. Checked with `validate_palette.js` of the `dataviz` skill on the white surface, 23 Nov 2026: lightness band, chroma floor, colour-blind separation (worst neighbours ΔE 9.0) and normal-vision floor all pass. Aqua, yellow, magenta and cyan are below 3:1 against white, so a type colour is never the only carrier: every segment has its name and count in the list under the bar.

| Type | Colour |
| --- | --- |
| Feature work | `#2a78d6` |
| Bug fix | `#eb6834` |
| Test change | `#1baf7a` |
| Refactor and performance | `#eda100` |
| Security | `#e34948` |
| Infra and release | `#12b5d4` |
| Documentation | `#e87ba4` |
| Review comment written | `#008300` |

Tier, complexity and score are ordered, so they take one blue from light to dark: T1 and Low `#86b6ef`, Medium `#3987e5`, T2 `#2a78d6`, High `#1c5cab`, T3 and Very high `#0d366b`. "Not judged yet" is the neutral `#c3cfdb`. Status (a gate's outcome, a strength, a gap) uses green `#0a8f3c`, amber `#b86e00` and red `#c93434` as text or icon colour and always comes with a word.

Text never takes a series colour; a swatch next to the text carries the identity.

**Type.** Self-hosted through the `@fontsource` packages, so a build needs no call to a font service.
- Display: Bricolage Grotesque (variable), for page titles, panel titles and the large numerals.
- Body: IBM Plex Sans 400, 500, 600.
- Data: IBM Plex Mono 400, 500, for PR numbers, tiers, dates, eyebrow labels and axis values.

**Layout.** A 1600 px content column (widened from 1180 px on 23 Nov 2026 so wide screens are used). The profile, the overview and the roster use a 12-column grid that falls to one column under 760 px.

**Signature: the tier steps.** Three steps that rise from T1 to T3. The steps up to the working tier are filled; the step a level expects carries a sun-yellow marker. It is on each dimension card and, small, on each roster row. It shows in one figure what the why line says in words.

**Surfaces.** White cards, 1 px hairline, 14 px radius, one soft shadow. No coloured left borders. The page header is a navy band with a faint graph-paper grid and an azure glow.

**Motion.** Bars and rings grow in once on load (CSS only, 600 ms), switched off under `prefers-reduced-motion`. Nothing else moves.

## Chart forms

Chosen by the job of the data (`dataviz`, "Choosing a form"):

| Data | Form |
| --- | --- |
| Hygiene share; tested share | Ring meter with the percent in the middle, `role="meter"` |
| Work classification (up to 14 classes); comment types (10) | Ranked rows: name and count that open, a thin bar in one azure, the percent |
| Work complexity, person and repository | Two segmented bars of the same length side by side, the rows that open under each |
| Comments by tier; tested by tier | One segmented bar in the blue ramp, the rows that open under it |
| A gate | A bullet chart: a 0 to 100 track, the pass zone and the close-miss zone shaded from the limits of the gate, a line at the repository's share, a dot at the person's share, both labelled |
| A dimension score | The numeral "n of 2" with two pips, next to the tier steps |
| Team: evidence by type | Ranked bars in the type colours |
| Team: scores and working tiers per dimension | Segmented bars in the blue ramp with the counts |
| Team: most evidence | One segmented bar per developer in the type colours |
| Roster row | A thin segmented bar in the type colours, the total, two score chips |

A share that is null shows "no PRs" and no meter (decision 234 holds). No chart has two axes. No pie or donut.

## Main flows

**Shell.** `web/app/layout.tsx` loads the fonts and renders `AppBar` above every page. `AppBar` shows the product mark and two links; the link of the current section is marked with `aria-current="page"` (a small client component reads the path).

**Overview `/`.** Reads `checkBackend`, `fetchDevelopers` and, when `DEFAULT_QUARTER` is set, the L2 scores of every developer (`fetchTeamScores`). Shows the header with the backend state ("backend up" or "backend down"), four stat tiles (developers, evidence items, review comments written, developers scored), "Evidence by type", "Scores at L2" and "Working tier" per dimension, and "Most evidence" (the eight developers with the most items, each a link to the profile). Without `DEFAULT_QUARTER` or before a scoring run the score panels say what is missing. With the backend down the page says so and shows no panel.

**Roster `/developers`.** One row per developer in the order of the backend: avatar with initials, the name (a link to the profile), the total and the segmented bar of the eight types, a score chip per dimension ("Coding and code quality: 2 of 2, T3" with small tier steps), a link to the evidence list. A developer with no scores shows "not scored". A legend of the eight types is above the rows.

**Profile.** Order unchanged (decision 259): header, notice, dimension cards, gates, analytics. The header band carries the name, the quarter, the level control and four figures from the analytics (hygiene, own PRs merged, PRs reviewed, substantive comments written). The dimension cards are two columns, the gates three, the analytics two.

**Evidence list.** A summary card under the header band with the counts per type as a segmented bar and a list whose entries jump to the sections; a type with no items has no section; each type that has items a titled group of cards with a type swatch; tier chips in the blue ramp.

**Item page.** The header band with type, date, quarter and author; a judgment card with the tier chip, the values next to it, the reason, the facts as linked rows, the provenance in mono.

**Uses.** `fetchDevelopers`, `fetchEvidenceItems`, `fetchAnalytics`, `fetchDimensionScores`, `fetchItemSummaries`, `fetchItem`, `checkBackend`; `profileText.ts`, `profileParams.ts`, `tierTable.ts`, `EVIDENCE_SECTIONS`.

**Gives.** `web/components/` (`AppBar`, `PageHeader`, `Panel`, `Notice`, `StatTile`, `Avatar`, `TierSteps`, `TierChip`, `charts/SegmentedBar`, `charts/RingMeter`, `charts/MeterBar`), `web/lib/palette.ts` (the colour of a type, a tier, a complexity, a score), `web/lib/team.ts` (`fetchTeamScores`, `teamSummary`).

## Edge cases

- Backend down: every page says "backend down" in a notice inside the shell.
- No developers; a developer with no items: the empty state names it; a bar with a total of 0 is an empty track, and the roster draws none for a developer with no items.
- No `DEFAULT_QUARTER`: the overview and the roster show evidence only and say that scores need the quarter; the profile keeps its notice.
- Not scored yet: the profile keeps "No scores for {quarter} yet"; roster rows say "not scored".
- A scores call that fails for one developer: that developer is "not scored"; the others are shown.
- A share that is null: "no PRs", no meter.
- A dimension with no working tier: the steps are all empty and the text says "no working tier".
- A gate that is unverified: no chart, the words "gate unverified" and the reason.
- A gate share above the repository's: the dot sits right of the line; the sentence under the chart says "above".
- Long titles and long developer names wrap; nothing scrolls sideways at 375 px.
- A segment narrower than its text has no text on it; the row under the bar carries the value.

## How it will be tested

- Unit tests, written first: `team.test.ts` (aggregation, a failed call), `palette.test.ts`, the chart components (segment widths, `role="meter"` and none for null, no bar for a total of 0), `TierSteps`, `AppBar`.
- The page tests of F05 and F12 keep every assertion on a number, a link and an order; they gain the new behaviours (overview panels, roster scores).
- "Done when" of the master plan: the five pages in the browser at 1280 px and 375 px with real data, screenshots in `notes.md`; the profile numbers of developer 9 against the two API answers; the click path from a score to a PR on GitHub; the palette check output; `npm run format:check`, `lint`, `typecheck`, `test`, `build`.

## Open points

None. Decisions made by the session without a brief, at the user's instruction (decision 261): 262 to 270 in `docs/decisions.md`.
