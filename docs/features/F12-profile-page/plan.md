# F12 Profile page: plan

Spec: `docs/features/F12-profile-page/design.md` (approved 22 Nov 2026, decisions 233 to 257).

Web code follows `web/AGENTS.md` (read the Next.js 16 guides in `web/node_modules/next/dist/docs/` before writing pages) and the F05 patterns: server components, `dynamic = "force-dynamic"`, CSS Modules, tests with `renderToStaticMarkup` and mocked `lib` functions. Load the `dataviz` skill before drawing the bars (batch 4).

## Batch 1: tasks 1 to 3 (backend reads for the item page)

### Task 1. Current judgment of an item, readable outside `judgment`
A public service `judgment.CurrentItemJudgments` with `ofItems(JudgmentKind kind, Collection<Long> evidenceItemIds)` returning, per item id, a public record `ItemJudgment` (kind, tier, value as `JsonNode`, reason, facts as `JsonNode`, judged by, model, prompt version, confidence, judged at). One query, current judgments only (not superseded). `judgment` keeps its rows hidden: callers see the record only.
- Files: `backend/src/main/java/com/dsinnovators/ei/judgment/CurrentItemJudgments.java`, `ItemJudgment.java`; test `backend/src/test/java/com/dsinnovators/ei/judgment/CurrentItemJudgmentsTest.java`.
- **Test first:** `CurrentItemJudgmentsTest.returnsTheCurrentJudgmentOfTheKindForEachItem`: an item with a superseded and a current `feature_work_complexity` judgment and an `item_tier` judgment gives only the current one of the asked kind; an item without one is not in the map.
- Commit: `feat(F12): read the current judgment of evidence items`

### Task 2. `GET /api/evidence-items?ids=`
In `api`, a new `EvidenceItemController` (`/api/evidence-items`). `ids` is a required comma-separated list of positive whole numbers, at most `ei.api.max-evidence-item-ids` (1000, in `application.yml`); otherwise 400 naming the value. Answers, for each id that exists (unknown ids are left out), in the order asked: `id, type, date, quarter, title, sourceUrl, links, person {personId, displayName}, tier` (the current tier of the item's kind, decision 241, null when none), `current` (false when another row supersedes it or it is withdrawn), `currentItemId` (the current row of the same key, null when none or when it is itself current). Title and links reuse the logic of `EvidenceItemResponse` without copying it. The type-to-kind mapping (decision 241) lives in one place in `api`.
- Files: `api/EvidenceItemController.java`, `api/EvidenceItemSummaryResponse.java`, a mapping class for decision 241, `api/DeveloperExceptionHandler.java` (400 for bad ids), `evidence` read methods as needed (`CurrentEvidenceItems` or `EvidenceItemRepository`: rows by ids, current row of a key), `application.yml`; test `api/EvidenceItemControllerTest.java`.
- **Test first:** `EvidenceItemControllerTest.returnsTheSummaryOfEachGivenIdWithItsPersonAndCurrentTier`; also superseded row marked `current: false` with `currentItemId`, unknown id left out, `ids=abc`, empty and 1001 ids give 400.
- Commit: `feat(F12): item summaries by id`

### Task 3. `GET /api/evidence-items/{id}`
The summary of task 2 plus `judgment`: `{state: "judged" | "not_judged_yet" | "not_tiered", kind, tier, value, reason, facts: [{fact, link}], model, promptVersion, judgedAt}` (fields null unless judged). Bug fix and Documentation are `not_tiered` (decision 241). Unknown id 404 with a message naming the id.
- Files: `api/EvidenceItemController.java`, `api/EvidenceItemDetailResponse.java`, `api/EvidenceItemNotFoundException.java`, `api/DeveloperExceptionHandler.java`; test `api/EvidenceItemControllerTest.java`.
- **Test first:** `EvidenceItemControllerTest.returnsTheItemWithItsCurrentJudgment` (Feature work with complexity, reason, facts, model, prompt version); also a review comment, a Test change `item_tier`, an item with no judgment (`not_judged_yet`), a Bug fix (`not_tiered`), unknown id 404.
- Commit: `feat(F12): one evidence item with its current judgment`

## Batch 2: tasks 4 to 6 (web clients and text)

### Task 4. Web clients for the profile and items
Move the shared fetch (`read`, `EvidenceResult`, timeout) of `web/lib/evidence.ts` to `web/lib/backend.ts` (no duplicate); `evidence.ts` keeps its functions on top of it. New `web/lib/profile.ts`: the types of the analytics and dimension-scores answers (design, "What F12 reads") and `fetchAnalytics(baseUrl, personId, quarter)`, `fetchDimensionScores(baseUrl, personId, quarter, level)`. New `web/lib/items.ts`: types and `fetchItemSummaries(baseUrl, ids)` (no call for an empty list) and `fetchItem(baseUrl, id)`.
- Files: `web/lib/backend.ts`, `backend.test.ts`, `evidence.ts`, `evidence.test.ts`, `profile.ts`, `profile.test.ts`, `items.ts`, `items.test.ts`.
- **Test first:** `profile.test.ts` "fetchDimensionScores calls the scores endpoint with quarter and level and returns the body"; also 404 → not-found, 500 and timeout → down, missing base URL → down; `items.test.ts` "fetchItemSummaries sends the ids comma-separated and makes no call for none".
- Commit: `feat(F12): web clients for analytics, scores and items`

### Task 5. Profile text: percents, why line, next step, gate words
Pure functions in `web/lib/profileText.ts`: `percent(share)` (whole percent, "no PRs" for null, decision 234); `whyLine(dimension, level)` (decision 236); `nextStepLine(dimension, level)` (design, "Next-step line"; fraction from `trail.settings.presenceThreshold`); `gateWords(gate)` (outcome and unverified reason in words, the difference and limits); the badge (`strength` at 2, `gap` at 0, decision 251). The spec 6.4 wording of the six types in `web/lib/tierTable.ts` (decision 242).
- Files: `web/lib/profileText.ts`, `profileText.test.ts`, `tierTable.ts`.
- **Test first:** `profileText.test.ts` "whyLine says the working tier, the level's tier and the score, and names a gate that held it"; also next step at L1/L2/L3, at score 2 (none), at L3 above T3, held by a gate, Review comment written left out when not read at the level, the L3 two-T3 addition; percent rounding 0.825 → 83%, null → "no PRs".
- Commit: `feat(F12): profile text generated from scores and the tier table`

### Task 6. URL parameters and the "not judged yet" count
`web/lib/profileParams.ts`: `profileParams(searchParams, defaultQuarter)` gives `{quarter, level}` or invalid (quarter `YYYY-Qn`, level L1 to L3, defaults `DEFAULT_QUARTER` and L2, decisions 237, 246, 247). `notJudgedYet(analytics, scores)`: distinct PR numbers without a digest and distinct item ids not judged yet across the analytics and the trails (decision 253). `itemIdsOnPage(analytics, scores)`: every item id the page can open (decision 254). `DEFAULT_QUARTER=2026-Q3` in `.env.example`.
- Files: `web/lib/profileParams.ts`, `profileParams.test.ts`, `.env.example`.
- **Test first:** `profileParams.test.ts` "takes the default quarter and L2 when the URL has none"; also wrong level and quarter → invalid, missing default quarter → invalid with a reason; `notJudgedYet` counts each PR and item once.
- Commit: `feat(F12): profile URL parameters and the not-judged-yet count`

## Batch 3: tasks 7 to 9 (item page, profile page, trail)

### Task 7. Item page `/items/[itemId]`
Read-only (decision 244): title, type, date, person (link to the profile), links to GitHub and OpenProject, the judgment (tier, value in words: complexity with "Very high (counts as T3)", comment type and substantive, test kind; reason; facts each with its link; model, prompt version, date), "not judged yet", "counted, not tiered", "no longer current" with a link to the current row (decision 238). Not-found for a non-number or unknown id; "backend down".
- Files: `web/app/items/[itemId]/page.tsx`, `page.test.tsx`, `item.module.css`.
- **Test first:** `page.test.tsx` "shows the AI judgment with its reason, facts and a link to the pull request on GitHub".
- Commit: `feat(F12): read-only item page`

### Task 8. Profile page: header, level control, dimension cards
`/developers/[personId]/profile`: header (name, quarter, level links L1 L2 L3 keeping the quarter, hygiene percent, links to the evidence list and the developer list), the not-judged-yet notice, one card per dimension (score, "works at T2", why line, next step, badge), "No scores for this quarter yet" when `scored` is false (decision 239). Calls analytics and scores in parallel, then the item summaries of `itemIdsOnPage`. Not-found for a bad person id, unknown person or bad URL parameter; "backend down".
- Files: `web/app/developers/[personId]/profile/page.tsx`, `page.test.tsx`, `profile.module.css`, `DimensionCard.tsx`.
- **Test first:** `page.test.tsx` "shows both dimension scores at the selected level with the why line" (mocked scores at L1 and L3 give the scores and lines of the mock).
- Commit: `feat(F12): profile page with dimension scores and the level control`

### Task 9. Drill-down, trail and gates panel
`Drill`: a native `<details>` fold under a number listing its items (title from the summaries, type, tier, link to `/items/{id}`) and its PRs (links to `https://github.com/opf/openproject/pull/{n}`); a count of 0 has nothing to open (decision 255). `Trail` in each card, folded (decision 252): per evidence type the presence rows (at or above, total, threshold, yes or no), counted items with tier and weight, not judged yet, not substantive, not read at the level; the L3 rule; counted-not-tiered items; notes. `GatesPanel` (decision 240): G1 and G2 with outcome, person and repository shares, difference, limits, group size, each PR list opening; G4 "gate unverified".
- Files: `web/app/developers/[personId]/profile/Drill.tsx`, `Trail.tsx`, `GatesPanel.tsx`, `page.tsx`, `page.test.tsx`, `profile.module.css`.
- **Test first:** `page.test.tsx` "a trail item opens to its item page and its pull request on GitHub".
- Commit: `feat(F12): trail, gates panel and drill-down to items and pull requests`

## Batch 4: tasks 10 and 11 (analytics, links)

### Task 10. The five analytics and hygiene as bars
`AnalyticsSection`: horizontal CSS bars (decision 257), each row with name, count, whole percent and a `Drill`: Work classification; Work complexity person and repository side by side (low, medium, high, very high, not judged yet); Review participation (PRs reviewed, authors reviewed, substantive per PR reviewed, by comment type, by tier, not judged yet); Review findings received (per PR, by type, by tier, not judged yet); Testing contribution (feature and fix PRs tested, Bug fix PRs tested, tested by tier, PRs without digest); Hygiene (linked, not linked). Repository numbers open too (decision 254).
- Files: `web/app/developers/[personId]/profile/AnalyticsSection.tsx`, `Bars.tsx`, `page.tsx`, `page.test.tsx`, `profile.module.css`.
- **Test first:** `page.test.tsx` "every analytics number on the page equals the API value" (each count and rounded share of a full mock appears, the work-classification percents of the mock add to 100).
- Commit: `feat(F12): analytics drawn as bars with drill-down`

### Task 11. Links into the profile
The developer list links each name to the profile (decision 250); the evidence list page links to the profile.
- Files: `web/app/developers/page.tsx`, `page.test.tsx`, `web/app/developers/[personId]/page.tsx`, `page.test.tsx`.
- **Test first:** `developers/page.test.tsx` "each developer links to their profile".
- Commit: `feat(F12): link the developer and evidence lists to the profile`

## Verify the whole feature

```
export JAVA_HOME=~/.jdks/temurin-25.0.5 && cd backend && ./mvnw verify
source ~/.nvm/nvm.sh && nvm use && cd web && npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```
