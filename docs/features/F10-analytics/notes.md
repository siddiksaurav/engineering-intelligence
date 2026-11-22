# F10 Five analytics and the hygiene number: notes

## Differences from the plan

- Batch 1: `identity.BotAccounts.loginsOfBots(Quarter)` was added (public) so the analytics SQL reuses the bot rule of decision 84 instead of reading `github.bot_logins` again; `isBot` uses it. `BotAccountsTest` has a test for it.
- Batch 1: shared pieces are `Count` (number, item ids, PR numbers), `Shares`, `Slice`, `Distribution` (with the `not_judged_yet` bucket), `AnalyticsSql` (named CTEs: latest PR version, human PRs merged in the quarter with their person, current item, current judgment, current digest) and `AnalyticsQuery`. The merge day is the UTC date of `mergedAt`. Test rows come from the test helper `AnalyticsRows`.
