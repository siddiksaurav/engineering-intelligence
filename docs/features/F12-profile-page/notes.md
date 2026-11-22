# F12 Profile page: notes

## Differences from the plan

- Batch 1: the title and link logic moved from `EvidenceItemResponse` into `api.ItemTitleAndLinks`, shared by the old and new responses (no copy). `CurrentItemTiers` now delegates to `CurrentItemJudgments`.
- The summary's `tier` is a plain string ("T2") or null; the reason is on the detail's `judgment`. `ids` is deduplicated in the order asked; the 1,000 limit counts the ids as given. A missing `ids` gives Spring's default 400.
- A withdrawn row has `current: false` and `currentItemId: null` (its key has no current row). `not_tiered` covers Bug fix and Documentation even if an `item_tier` row exists. The detail's `facts` is the stored `[{fact, link}]` array; `judgedAt` is an ISO instant.
- Batch 2: the shared fetch is `lib/backend.ts` `readBackend` with type `BackendResult` (no longer evidence-specific); log text "Backend request failed". `readBackend` takes an optional timeout for the test.
- Next-step wording: a gate-held score reads "To score N: G1 within X points of the repository" (N the score before gates; X `passWithinPoints` for 2, `farMissBeyondPoints` for 1); a type with no wording at the needed tier (Infra at T1) is skipped; fractions 1/2, 1/3, 1/4 in words, others "n/d".
- `notJudgedYet` counts PRs from work classification's not-judged slice, `testingContribution.withoutDigest` and every gate's `pullRequestsWithoutDigest`; items from the not-judged slices, both review `notJudgedYet` counts and the trails. A repeated URL parameter is invalid.
- This machine has no nvm (`~/.nvm/nvm.sh` missing): web checks ran on the system Node 22.22.1; `.nvmrc` and CI use 24.14.1.
- Batch 3: the item summaries are fetched in task 9, not 8 (nothing used them before). The missing `DEFAULT_QUARTER` notice is decided in the page. The gates panel takes the gates of the first dimension that has any: only Engineering standards has gates (decision 202), so this is decision 240 without naming the key. Trail item lists use their own fold (`TrailItems`) to show tier and weight. The item page adds `JudgmentView.tsx`; an `item_tier` value is listed key by key ("Test kind: integration").
- Batch 4: two colour tokens added to `globals.css` (`--complexity-low` #7dd3fc, `--not-judged` #94a3b8); Very high uses `--sky-900`. `.headerLinks` moved to `developers.module.css`, shared by the profile and evidence headers. The developer card became a `div` (a link inside a link is invalid): the name opens the profile, "Evidence list" keeps the old page. The analytics sit above the dimension cards, so they show when there are no scores yet.

- Review fixes (6 commits): superseded and withdrawn items marked "no longer current" on the profile (decision 238); the next-step line names every gate that would hold the target score (decision 258); missing item ids logged; the selected level readable (a CSS specificity bug made it white on white); dimension cards and gates before the analytics (decision 259); "Own PRs merged" in place of the wrong "PRs with findings"; the not-judged-yet notice counts only the person's PRs and items, with singular and plural.

## Verification

Run on 22 Nov 2026 on a throwaway copy of the Compose database (`ei_f12_check`, deleted afterwards), with a backend from this branch on port 8081 and the web on port 3001, so the running backend and the Compose database were not touched. The copy was migrated to V12 by that backend; `dimension-scoring` run 61 scored 26 persons, 156 rows.

- **Full suites.** Backend `./mvnw verify`: 988 tests, 0 failures (backend unchanged after batch 1). Web: format, lint, typecheck, 171 tests in 14 files, build: green after the review fixes.
- **Done when 1: from a dimension score down to a PR on GitHub.** Browser, person 11 (klaustopher), L2: Coding and code quality, Trail, Feature work "Implement process to allocate real users to generic requests" → `/items/2` (Feature work, T2, Medium, reason, four facts with links, model `openai/gpt-oss-120b`, prompt `feature-work-complexity/v1`) → "Pull request #23912 on GitHub" → `https://github.com/opf/openproject/pull/23912`. Web test "a trail item opens to its item page and its pull request on GitHub".
- **Done when 2: changing the level changes the scores and the trail explains why.** Browser, person 9 (oliverguenther) at phone width: L2 gives Coding 2 ("Works at T3. L2 expects T2: 2.", strength) and Engineering standards 1; clicking L3 gives Coding 1 ("Works at T3. L3 expects T3: 1." and "T3 is the top tier in the PoC, so 1 is the best score at L3.") and Engineering standards 0. Person 11, L2: Engineering standards "Works at T2. L2 expects T2: 1, held to 0 by G2 (far miss)." with G2 at 0% against the repository's 34.9% (0 of 5 Bug fix PRs tested, 22 of 63). Web tests at L1 and L3.
- **Done when 3: the numbers equal the API values of F10 and F11.** A script (`compare.py`, scratchpad, not committed) read the analytics and dimension-scores APIs and the rendered page for persons 11, 9 and 1 at L1, L2 and L3: every count and every share (whole percent) of each analytic appears in its section, the header hygiene (77%), each card's "score of 2, works at tier" and each gate outcome match: 9 of 9 "all numbers equal the API". Web test "every analytics number on the page equals the API value".
- **Phone width.** 375 px: no horizontal scroll (`scrollWidth` 375).
- **Waiting for the user.** On the Compose database itself: restart the backend on `main` (it migrates to V12), run `dimension-scoring` for 2026-Q3 after the model runs, then open `localhost:3000/developers` and a profile. Most numbers say "not judged yet" until the model jobs finish (22 Nov: 60 of 172 PR digests).

## Follow-ups

From the review (decision 75), not fixed:
- `web/lib/profileParams.ts` `itemIdsOnPage` and the page: one ids call with no chunking; above the backend's 1,000 ids (or Tomcat's 8 KB header) the call fails and every item shows as "item #N". Matters once F09 runs a month.
- `web/app/developers/[personId]/profile/page.tsx`: the missing `DEFAULT_QUARTER` check duplicates the branch in `profileParams`, which the page can no longer reach.
- Small web duplications: `percent()` against `Math.round` in `Bars.tsx` `aria-valuenow`; `NOT_JUDGED_YET` in `Bars.tsx` and `profileParams.ts`; `LEVELS` in `page.tsx` and `profileParams.ts`; the evidence type title lookup three times (`Bars.plainName`, `Drill`, item page); `TrailItems` repeats the fold of `Drill`; `GatesPanel` writes its own "gate unverified" instead of `gateWords().outcome`.
- `Bars.tsx` `colorOf` builds a CSS variable name from a class name by regex: fragile.
- `GatesPanel.tsx`: an unverified G1 or G2 hides its PR lists and group size; the group size never opens to its persons (decisions 232, 254).
- Gate shares show one decimal ("66.7%") while decision 234 says whole percents for shares; kept because the gate compares unrounded shares and the points difference needs the decimal.
- The trail shows "T3, weight 4" for a Very high item, not the complexity; gate results are in the gates panel, not inside the trail (decision 252 lists them).
- `judgment.ItemJudgment.judgedBy` and `confidence` are read by nothing yet (F08 item page may show them).
- `api.DeveloperExceptionHandler` now also serves `EvidenceItemController`; the name no longer fits.
- Tests: no backend test for de-duplicated `ids` or for Refactor, Security and Infra mapping to `item_tier`; the analytics-equality web test uses unanchored strings; "shows no notice when everything is judged" relies on capitalisation; work-classification percents add to 100 only for the chosen data (plain rounding does not guarantee it).
- The item page's person link drops the quarter: an item from 2026-Q2 opens the profile at `DEFAULT_QUARTER`.

## What a later feature should know

- Routes: `/developers/{personId}/profile?quarter=&level=` (defaults `DEFAULT_QUARTER` from `.env` and L2) and `/items/{itemId}`. F08 extends the item page (`web/app/items/[itemId]/`) with the source panel and the human label.
- Endpoints: `GET /api/evidence-items?ids=` (at most `ei.api.max-evidence-item-ids`, 1,000) and `GET /api/evidence-items/{id}` with `judgment.state` judged, not_judged_yet or not_tiered. The evidence type to judgment kind mapping is `api.ItemJudgmentKind` (decision 241); `judgment.CurrentItemJudgments` reads the current judgment of items.
- Web shared fetch: `web/lib/backend.ts` `readBackend`; profile text in `web/lib/profileText.ts`, the spec 6.4 wording in `web/lib/tierTable.ts`.
- To check on real data without touching the Compose database: copy it with `pg_dump | psql` into a new database, start a backend with `POSTGRES_DB=<copy> BACKEND_PORT=8081` and the web from a separate worktree (two `next dev` cannot share one `web/` folder) on another port.
