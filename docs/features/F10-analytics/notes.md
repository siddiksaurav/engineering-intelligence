# F10 Five analytics and the hygiene number: notes

## Differences from the plan

- Batch 1: `identity.BotAccounts.loginsOfBots(Quarter)` was added (public) so the analytics SQL reuses the bot rule of decision 84 instead of reading `github.bot_logins` again; `isBot` uses it. `BotAccountsTest` has a test for it.
- Batch 1: shared pieces are `Count` (number, item ids, PR numbers), `Shares`, `Slice`, `Distribution` (with the `not_judged_yet` bucket), `AnalyticsSql` (named CTEs: latest PR version, human PRs merged in the quarter with their person, current item, current judgment, current digest) and `AnalyticsQuery`. The merge day is the UTC date of `mergedAt`. Test rows come from the test helper `AnalyticsRows`.
- Batch 2: `AnalyticsSql` gained a `merged_pull_request` CTE (every author, with a `human_author` flag) from which `human_pull_request` is derived; `ReviewCommentBreakdown` (public record) is the shared select and parser of substantive comments by type and tier, used by Review participation and Review findings received. The set of PRs reviewed is a CTE inside `ReviewParticipation` only.
- Batch 2: a Test change item is found for a PR by its key `test_change:<prExternalId>` (as `TestChangeBuilder` keys it); Bug fix PRs come from `evidence_item_source` role `pull_request`, which covers both Bug fix kinds. A Test change item without an `item_tier` judgment counts as `not_judged_yet` in the tier split.
