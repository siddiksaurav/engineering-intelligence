# F12 Profile page: notes

## Differences from the plan

- Batch 1: the title and link logic moved from `EvidenceItemResponse` into `api.ItemTitleAndLinks`, shared by the old and new responses (no copy). `CurrentItemTiers` now delegates to `CurrentItemJudgments`.
- The summary's `tier` is a plain string ("T2") or null; the reason is on the detail's `judgment`. `ids` is deduplicated in the order asked; the 1,000 limit counts the ids as given. A missing `ids` gives Spring's default 400.
- A withdrawn row has `current: false` and `currentItemId: null` (its key has no current row). `not_tiered` covers Bug fix and Documentation even if an `item_tier` row exists. The detail's `facts` is the stored `[{fact, link}]` array; `judgedAt` is an ISO instant.

## Verification

## Follow-ups
