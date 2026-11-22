# F12 Profile page: notes

## Differences from the plan

- Batch 1: the title and link logic moved from `EvidenceItemResponse` into `api.ItemTitleAndLinks`, shared by the old and new responses (no copy). `CurrentItemTiers` now delegates to `CurrentItemJudgments`.
- The summary's `tier` is a plain string ("T2") or null; the reason is on the detail's `judgment`. `ids` is deduplicated in the order asked; the 1,000 limit counts the ids as given. A missing `ids` gives Spring's default 400.
- A withdrawn row has `current: false` and `currentItemId: null` (its key has no current row). `not_tiered` covers Bug fix and Documentation even if an `item_tier` row exists. The detail's `facts` is the stored `[{fact, link}]` array; `judgedAt` is an ISO instant.
- Batch 2: the shared fetch is `lib/backend.ts` `readBackend` with type `BackendResult` (no longer evidence-specific); log text "Backend request failed". `readBackend` takes an optional timeout for the test.
- Next-step wording: a gate-held score reads "To score N: G1 within X points of the repository" (N the score before gates; X `passWithinPoints` for 2, `farMissBeyondPoints` for 1); a type with no wording at the needed tier (Infra at T1) is skipped; fractions 1/2, 1/3, 1/4 in words, others "n/d".
- `notJudgedYet` counts PRs from work classification's not-judged slice, `testingContribution.withoutDigest` and every gate's `pullRequestsWithoutDigest`; items from the not-judged slices, both review `notJudgedYet` counts and the trails. A repeated URL parameter is invalid.
- This machine has no nvm (`~/.nvm/nvm.sh` missing): web checks ran on the system Node 22.22.1; `.nvmrc` and CI use 24.14.1.

## Verification

## Follow-ups
