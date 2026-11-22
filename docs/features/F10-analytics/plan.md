# F10 Five analytics and the hygiene number: plan

Spec: `docs/features/F10-analytics/design.md` (approved 22 Nov 2026). Decisions 179 to 200.

All classes are in the new package `com.dsinnovators.ei.analytics` unless named otherwise. Each analytic is one read-only SQL query through Spring's `JdbcClient` (decision 179). The rules shared by several queries (the latest version of a stored PR, a human author, the person of an account, the current item, the current judgment, the merge day in the quarter) are written once and reused, not copied into each query. Every count carries the ids of its evidence items and the numbers of its PRs (decision 199). Shares are count / total, unrounded, null when the total is 0 (decision 186). Tests extend `DatabaseTest` and insert their own rows.

## Batch 1: tasks 1 to 3

### Task 1. Hygiene, and the package
Builds the package with the shared pieces (a count with its item ids and PR numbers, a share) and the repository's hygiene number: of the human-authored PRs merged in the quarter, the share with at least one `resolved` link in `pull_request_work_item` (decisions 104, 193). Enables `SpecNumbersTest.theAnalyticsPackageHoldsNoNumberConstant`.
- Files: `analytics/RepositoryHygiene.java`, the shared count and share types, the shared SQL pieces; `architecture/SpecNumbersTest.java`; `analytics/RepositoryHygieneTest.java`.
- **Test first:** `RepositoryHygieneTest.sharesTheHumanPullRequestsOfTheQuarterLinkedToAWorkItem`: three human PRs (one linked `resolved`, one only `not_visible`, one none), one bot PR, one PR merged in another quarter, one PR stored in two versions → 1 of 3, the PR numbers listed.
- Commit: `feat(F10): repository hygiene and the analytics package`

### Task 2. Work classification
The person's human-authored PRs merged in the quarter, counted by the `workType` of the current `pull_request_digest` of the PR (any version of its record); a PR without one counts as "not judged yet" (decisions 189, 197, 198). Shares add up to 1.
- Files: `analytics/WorkClassification.java`; `analytics/WorkClassificationTest.java`.
- **Test first:** `WorkClassificationTest.countsEachMergedPullRequestOfThePersonByItsDigestWorkType`: two feature PRs, one fix PR, one PR without digest and with no evidence item, one PR of another person, a superseded digest → feature 2, fix 1, not judged yet 1, shares sum to 1.
- Commit: `feat(F10): work classification`

### Task 3. Work complexity
The person's current Feature work items dated in the quarter by the `complexity` of their current `feature_work_complexity` judgment: low, medium, high, very high, not judged yet (decisions 194, 198); next to it the same for every current Feature work item of the quarter in the repository, the person included (decision 195). Counts and shares.
- Files: `analytics/WorkComplexity.java`; `analytics/WorkComplexityTest.java`.
- **Test first:** `WorkComplexityTest.countsThePersonsFeatureWorkByComplexityNextToTheRepository`: the person's items low, high, very high and one unjudged; another person's medium item; a withdrawn item and a superseded judgment not counted → person and repository buckets as expected, with item ids.
- Commit: `feat(F10): work complexity`

## Batch 2: tasks 4 to 6

### Task 4. Review participation and Review effectiveness
PRs reviewed in the quarter (merge day): someone else's PR on which the person left a stored review of any state or a current Review comment written item (decision 192); different authors reviewed (persons; a bot's PR adds no author, decision 184); substantive comments (current `review_comment` judgment with `value.substantive` true) and substantive comments per PR reviewed; the mix by `commentType`; substantive comments by tier; comments not judged yet.
- Files: `analytics/ReviewParticipation.java`; `analytics/ReviewParticipationTest.java`.
- **Test first:** `ReviewParticipationTest.countsPullRequestsReviewedAuthorsAndSubstantiveCommentsPerPullRequest`: an approval without comment, a PR with two substantive comments (T1, T3) and one style comment, a comment on a bot's PR, a review on the person's own PR, an unjudged comment → PRs reviewed 3, authors 1, substantive 2, per PR 2/3, mix and tiers as expected.
- Commit: `feat(F10): review participation and effectiveness`

### Task 5. Review findings received
On the person's human-authored PRs merged in the quarter: current Review comment written items with `fields.pullRequestAuthorPersonId` = the person and a substantive current judgment; per PR (divided by all those PRs, decision 185), by comment type and by tier; non-substantive left out; unjudged counted apart.
- Files: `analytics/ReviewFindingsReceived.java`; `analytics/ReviewFindingsReceivedTest.java`.
- **Test first:** `ReviewFindingsReceivedTest.countsSubstantiveCommentsReceivedPerPullRequestLeavingStyleOut`: two PRs, one with a correctness (T2) and a style comment, one with none → 1 substantive, 0.5 per PR, style not counted.
- Commit: `feat(F10): review findings received`

### Task 6. Testing contribution
Of the person's PRs merged in the quarter whose current digest work type is feature, enhancement or fix (decision 190), the share with a current Test change item (decision 182); PRs without a digest counted apart. Of the PRs of the person's current Bug fix items in the quarter, both kinds (decision 183), the share with a current Test change item of any kind (decision 191). Tested PRs split by the Test change item's current `item_tier`.
- Files: `analytics/TestingContribution.java`; `analytics/TestingContributionTest.java`.
- **Test first:** `TestingContributionTest.sharesFeatureAndFixPullRequestsWithATestChange`: a feature PR with a T1 Test change, an enhancement PR without, a fix PR with a T2 Test change, a docs PR, a PR without digest → 2 of 3, tiers T1 1 and T2 1, 1 without digest.
- Commit: `feat(F10): testing contribution`

## Batch 3: task 7

### Task 7. The analytics endpoint
`GET /api/developers/{personId}/analytics?quarter=2026-Q3` in package `api` returns the person, the quarter, the five analytics and the hygiene number (decision 200), through one service in `analytics`. 404 for an unknown person (as decision 109), 400 for a missing or wrong quarter (decision 187), with a message that names the value.
- Files: `analytics/DeveloperAnalytics.java` (the service), `api/DeveloperAnalyticsController.java` and its response records, `api/DeveloperExceptionHandler.java`; `api/DeveloperAnalyticsControllerTest.java`.
- **Test first:** `DeveloperAnalyticsControllerTest.returnsTheFiveAnalyticsAndTheHygieneNumberOfTheQuarter`; then `answers404ForAnUnknownPerson`, `answers400ForAWrongQuarter`.
- Commit: `feat(F10): developer analytics endpoint`
