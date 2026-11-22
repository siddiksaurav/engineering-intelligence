# F10 Five analytics and the hygiene number: notes

## Differences from the plan

- Batch 1: `identity.BotAccounts.loginsOfBots(Quarter)` was added (public) so the analytics SQL reuses the bot rule of decision 84 instead of reading `github.bot_logins` again; `isBot` uses it. `BotAccountsTest` has a test for it.
- Batch 1: shared pieces are `Count` (number, item ids, PR numbers), `Shares`, `Slice`, `Distribution` (with the `not_judged_yet` bucket), `AnalyticsSql` (named CTEs: latest PR version, human PRs merged in the quarter with their person, current item, current judgment, current digest) and `AnalyticsQuery`. The merge day is the UTC date of `mergedAt`. Test rows come from the test helper `AnalyticsRows`.
- Batch 2: `AnalyticsSql` gained a `merged_pull_request` CTE (every author, with a `human_author` flag) from which `human_pull_request` is derived; `ReviewCommentBreakdown` (public record) is the shared select and parser of substantive comments by type and tier, used by Review participation and Review findings received. The set of PRs reviewed is a CTE inside `ReviewParticipation` only.
- Batch 2: a Test change item is found for a PR by its key `test_change:<prExternalId>` (as `TestChangeBuilder` keys it); Bug fix PRs come from `evidence_item_source` role `pull_request`, which covers both Bug fix kinds. A Test change item without an `item_tier` judgment counts as `not_judged_yet` in the tier split.

## Review

One review round (decision 75). Blocking: three filters without a test (person filter of the feature-and-fix query, quarter filters of the Bug fix and findings-received queries); fixed test first. Also fixed from the follow-ups: one repeatable-read snapshot for all queries (the model jobs write meanwhile), the human-author rule now exactly that of `BotAccounts.isBot`, the Test change found through its source PR instead of its key, javadoc of `Distribution`, `ReviewCommentBreakdown` and `Count`, an unused CTE removed, and `.env.example` no longer has an empty `LLM_PROVIDER_2_MODEL=` line that would cancel decision 177's default. The fix commits between `5af7c12` and `fa88c32` may not compile one by one; the tip is green.

## Follow-ups

- `analytics/ReviewCommentBreakdown.java`: the comment-type mix and the tier split hold judged comments only, with `notJudgedYet` apart (decision 198 applied only in part). While most comments are unjudged, the mix can read 100% from one comment. F12 must show `notJudgedYet` next to the mix.
- `analytics/TestingContribution.java`, `bugFixPullRequests`: the counts carry the Test change item ids, not the Bug fix item ids the PRs come from; an untested count has no item ids (decision 199 in part). F12's drill-down opens such a count by PR number.
- Tests the design asks for that rest on shared, already tested SQL: WorkClassification without a PR outside the quarter; TestingContribution without a superseded digest or a withdrawn Test change; ReviewFindingsReceived and ReviewParticipation without a superseded item or judgment.
- `analytics/AnalyticsQuery.countsByBucket` fills a map inside a `RowMapper` and drops the list; collect then build would be clearer.
- Small duplications: `WorkComplexity.BARS` repeats the `Complexity` enum names; the SQL literals `tested`, `untested`, `without_digest`, `not_judged_yet` repeat Java constants; `DeveloperAnalyticsResponse` copies `DeveloperAnalytics.Result`; `ReviewCommentBreakdown` holds both a result and its SQL.
- `ReviewCommentBreakdown.of` sorts tiers in a `TreeMap`, which fails on a null tier; `ReviewCommentTypes` guarantees a tier for every substantive type today.
- `.claude/launch.json` entry `backend` (committed with decision 177's change) hard-codes a `JAVA_HOME` and blanks `LLM_PROVIDER_2_API_KEY`, which turns off the Gemini fallback for whoever uses it. Kept as the user's local start setting; the user decides.
- The repeatable-read test checks the annotation only, not snapshot behaviour under concurrent writes.
