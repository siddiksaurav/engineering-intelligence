# F05 Evidence builders without AI, and the evidence list page: plan

Spec: `docs/features/F05-evidence-builders/design.md` (approved 22 Nov 2026; decisions 100 to 124).

Each task: test first, then code, then one commit. Run only the tests the task touches, plus the architecture tests when the task adds a table, entity or package (decision 73).

## Batch 1: tasks 1 to 3 (tables and the item store)

### Task 1. Migration V8 and the append-only trigger
Builds `evidence_item`, `evidence_item_source`, `evidence_item_link` with the columns of design "Tables (migration V8)", the index on `item_key`, unique `supersedes_id`, the check on `evidence_type`, and a row trigger refusing update and delete on `evidence_item` (as V2 does for `raw_record`). Adds the three tables to the `truncate` line of `DatabaseTest`. Enables `AppendOnlyTablesTest.evidenceItemHasATriggerBeforeEveryUpdateAndDelete`.
- Files: `backend/src/main/resources/db/migration/V8__evidence_item.sql`; `backend/src/test/java/com/dsinnovators/ei/DatabaseTest.java`; `backend/src/test/java/com/dsinnovators/ei/architecture/AppendOnlyTablesTest.java`; new `backend/src/test/java/com/dsinnovators/ei/evidence/EvidenceItemTableTest.java`.
- **Test first:** `AppendOnlyTablesTest.evidenceItemHasATriggerBeforeEveryUpdateAndDelete` (enabled) fails; then `EvidenceItemTableTest.anUpdateOfAnEvidenceItemIsRefused` and `aSecondRowCannotSupersedeTheSameRow`.
- Commit: `feat(F05): evidence_item tables, never updated or deleted`

### Task 2. The entity, its source rows and its repository
`EvidenceType` enum (`FEATURE_WORK`, `BUG_FIX`, `REVIEW_COMMENT_WRITTEN`, stored as the lower-case names of the table check), immutable `EvidenceItem` entity with its `EvidenceItemSource` rows (role `pull_request`, `work_package`, `review_comment`), and `EvidenceItemRepository` with no update or delete method: find the current row of a key, the current rows of all keys, the current rows of a person. Enables `AppendOnlyCodeTest.theEvidenceItemEntityIsImmutable`, `noRepositoryOfEvidenceItemCanUpdateOrDelete` and `SpecNumbersTest.theEvidencePackageHoldsNoNumberConstant`.
- Files: new `backend/src/main/java/com/dsinnovators/ei/evidence/` `EvidenceType.java`, `EvidenceItem.java`, `EvidenceItemSource.java`, `SourceRole.java`, `EvidenceItemRepository.java`; the two architecture test classes; new `EvidenceItemRepositoryTest.java`.
- **Test first:** `EvidenceItemRepositoryTest.theCurrentRowOfAKeyIsTheOneNoRowSupersedes` (a withdrawn current row counts as no current item).
- Commit: `feat(F05): evidence item entity and repository, append-only`

### Task 3. The item store: create, unchanged, supersede, withdraw
`BuiltItem` (key, type, person, project, date, source URL, fields as `JsonNode`, sources as raw record id and role) and `EvidenceItemStore`: `write(BuiltItem)` returns `CREATED`, `UNCHANGED` or `SUPERSEDED` by comparing the content hash (`CanonicalJson.sha256Hex` over all fields, sources included) with the current row of the key; `withdraw(key)` writes a withdrawn row superseding the current one; `currentKeys()`. One transaction per call (decisions 106, 123).
- Files: new `evidence/BuiltItem.java`, `evidence/EvidenceItemStore.java`, `evidence/WriteOutcome.java`; new `EvidenceItemStoreTest.java`.
- **Test first:** `EvidenceItemStoreTest.writingTheSameItemTwiceWritesOneRow`; then `aChangedItemSupersedesTheCurrentRow`, `aNewSourceVersionSupersedes`, `withdrawingWritesARowThatEndsTheItem`, `anItemWrittenAfterItsWithdrawalStartsAgainFromTheWithdrawnRow`.
- Commit: `feat(F05): evidence item store, a new row only when an item changes`

## Batch 2: tasks 4 to 7 (readers and the two work item builders)

### Task 4. Readers of stored pull requests and review comments
In `source.github` (decision 108): `StoredPullRequests.all()` (the latest version of every stored PR, in merge order) and `StoredReviewComments.all()` (the latest version of every stored inline review comment), returning `RawRecord`s; plus small typed readers the builders need (author login, typename, databaseId; PR number, title, url, mergedAt; comment databaseId, url, path, createdAt, replyTo, PR number and repository). Keep JSON paths inside `source.github`.
- Files: `source/github/StoredPullRequests.java`, new `source/github/StoredReviewComments.java` and record types as needed; tests in `source/github/`.
- **Test first:** `StoredReviewCommentsTest.allGivesTheLatestVersionOfEachComment`.
- Commit: `feat(F05): readers of every stored pull request and review comment`

### Task 5. Work package facts for the builders
In `source.openproject`: `StoredWorkPackages.firstDoneAt(number)`: the instant of the first status change into a done status, each change checked against `openproject.done_statuses` of the quarter of its UTC day (decision 107), empty when never done. A public `WorkPackageFacts` reader (number, title, type, `isBug`, project id, priority, url `data_source.base_url` + `/wp/{number}`) for a stored work package.
- Files: `source/openproject/StoredWorkPackages.java`, new `source/openproject/WorkPackageFacts.java` (or a record plus method on `StoredWorkPackages`); `StoredWorkPackagesTest.java`.
- **Test first:** `StoredWorkPackagesTest.firstDoneAtIsTheFirstChangeIntoADoneStatus` (with "tested" then "closed", and a change before the quarter's setting).
- Commit: `feat(F05): first done day and facts of a stored work package`

### Task 6. Feature work builder
`FeatureWorkBuilder`: from all stored PRs, their `resolved` links (decision 104), bots removed (`BotAccounts`, decision 102) and authors mapped by `PersonDirectory` (a human with no person is a failure of that item), builds one `BuiltItem` per (Feature, author) for Features with a first done day (decisions 112, 113): tasks = the non-Bug work items the author's PRs link under that Feature (decision 118), PRs = those PRs; a PR with several Features joins each (116); a Bug never joins (117); a work item with no Feature ancestor gives nothing (115). Project = the Feature's project (105). Key `feature_work:<feature>:<personId>`. Fields as in the design.
- Files: new `evidence/FeatureWorkBuilder.java` (and a small shared `evidence/LinkedPullRequests.java` if the Bug fix builder needs the same link reading); `FeatureWorkBuilderTest.java`.
- **Test first:** `FeatureWorkBuilderTest.anImplementationUnderAFeatureJoinsItsAuthorsGroup`; then a Bug under the Feature stays out, two Features on one PR, no Feature ancestor gives nothing, a Feature not yet done gives nothing, date is the first done day, two authors give two groups, a bot author gives nothing.
- Commit: `feat(F05): Feature work builder`

### Task 7. Bug fix builder
`BugFixBuilder`: one `BuiltItem` per (Bug, PR author) (decision 103) for Bugs with a first done day (114); PRs = that author's PRs linking the Bug; `underFeature` from `nearestFeature`, else null; project = the Bug's. Key `bug_fix:<bug>:<personId>`.
- Files: new `evidence/BugFixBuilder.java`; `BugFixBuilderTest.java`.
- **Test first:** `BugFixBuilderTest.aBugUnderAFeatureIsABugFixWithItsFeatureNamed`; then a PR with a Bug and an Implementation appears in both builders' output, an open Bug gives nothing, two authors give two items.
- Commit: `feat(F05): Bug fix builder`

## Batch 3: tasks 8 and 9 (review comments and the job)

### Task 8. Review comment written builder
`ReviewCommentBuilder`: one `BuiltItem` per stored inline review comment (decision 119) whose author is human and not the PR author, replies included (120), on any PR, a bot's included (111). Date = the PR's merge day, UTC (121). No project. Source link = the comment URL. Sources: the comment and the PR. Key `review_comment_written:<commentDatabaseId>`.
- Files: new `evidence/ReviewCommentBuilder.java`; `ReviewCommentBuilderTest.java`.
- **Test first:** `ReviewCommentBuilderTest.aHumanReviewersCommentIsAnItemDatedByTheMergeDay`; then a bot comment and the author's own comment give nothing, a reviewer's reply is an item.
- Commit: `feat(F05): Review comment written builder`

### Task 9. Job `evidence-build`
`EvidenceBuildJob` (`name()` `evidence-build`, no arguments, decision 100): runs the three builders, writes each item through `EvidenceItemStore` in its own transaction, withdraws every current key not built, counts `items_created`, `items_superseded`, `items_unchanged`, `items_withdrawn`, the same per type (`feature_work_created` ...), `pull_requests_read`, `bot_records_skipped`, `items_failed`; a failed item is counted and the run fails at the end naming the first 20 keys (`FailedItems`) with a new `EvidenceBuildFailedException`.
- Files: new `evidence/EvidenceBuildJob.java`, `evidence/EvidenceBuildFailedException.java`; `EvidenceBuildJobTest.java` (through `JobEndpointClient`, as the F04 job test does).
- **Test first:** `EvidenceBuildJobTest.aSecondRunWritesNoRow`; then a removed link withdraws the group, a new PR supersedes it, an author with no person fails the run at the end and the other items are written, unknown argument refused.
- Commit: `feat(F05): job evidence-build`

## Batch 4: tasks 10 to 12 (API and web pages)

### Task 10. Developer and evidence API
In `api` (decision 109): `GET /api/developers` → every person with `personId`, `displayName`, `counts` {`featureWork`, `bugFix`, `reviewCommentWritten`} of current items; `GET /api/developers/{personId}/evidence-items` → `person` and `items` [{`id`, `type`, `date`, `quarter`, `title`, `sourceUrl`, `links` [{`label`, `url`}], `fields`}], newest first; 404 with a message naming the id for an unknown person. A public read service in `evidence` (`CurrentEvidenceItems`) gives the current, non-withdrawn rows.
- Files: new `api/DeveloperController.java`, response records, `evidence/CurrentEvidenceItems.java`; `DeveloperControllerTest.java`.
- **Test first:** `DeveloperControllerTest.theItemsOfADeveloperAreTheCurrentOnesNewestFirst` (a superseded and a withdrawn row are not returned).
- Commit: `feat(F05): API of developers and their evidence items`

### Task 11. Web client of the evidence API
`web/lib/evidence.ts`: typed `fetchDevelopers(backendUrl)` and `fetchEvidenceItems(backendUrl, personId)`, server-side as `lib/health.ts`; a backend that is down or answers an error gives a "backend down" result, 404 gives "not found".
- Files: new `web/lib/evidence.ts`, `web/lib/evidence.test.ts`.
- **Test first:** `evidence.test.ts`: "fetchEvidenceItems returns the items of a developer".
- Commit: `feat(F05): web client of the evidence API`

### Task 12. Developer list page and developer page
`/developers`: each developer with a count per type, linking to `/developers/[personId]`. `/developers/[personId]`: three sections (Feature work, Bug fix, Review comment written), review comments grouped per PR, every item with date, quarter and its GitHub and OpenProject links; "No evidence items" when empty; "backend down" when the backend is down; a link from the home page. CSS Modules, bold sky-blue colours, no purple, readable at phone width (decisions 110, 122).
- Files: new `web/app/developers/page.tsx`, `web/app/developers/[personId]/page.tsx`, CSS modules, tests next to them; `web/app/page.tsx` (link).
- **Test first:** `app/developers/[personId]/page.test.tsx`: "shows the three sections with their links".
- Commit: `feat(F05): developer list and developer evidence pages`
