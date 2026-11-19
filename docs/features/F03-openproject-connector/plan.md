# F03 OpenProject connector: plan

**Spec:** `docs/features/F03-openproject-connector/design.md` (approved 19 Nov 2026). Its tables, flows, edge cases and decisions 56 to 69 carry the detail; this plan gives the order.

Backend packages: `source.github` (shared import service, stored pull request reader), `source.openproject` (everything else). Tests use recorded answers on a mock server and never call OpenProject or GitHub.

## Task 1. Table and setting

Migration V6: table `pull_request_work_item` with its checks, unique key and index (design, "New table"); setting `openproject.done_statuses` (decision 67). `DatabaseTest` also empties `pull_request_work_item`.

- Files: `backend/src/main/resources/db/migration/V6__openproject_links_and_done_statuses.sql`, `DatabaseTest.java`, `SchemaTest.java`, new `source/openproject/OpenProjectDoneStatusesSettingTest.java`.
- **Test first:** `SchemaTest.pullRequestWorkItemRefusesAResolvedLinkWithoutAWorkPackage` checks that the state check holds; `OpenProjectDoneStatusesSettingTest.doneStatusesAreTheFiveOfDecision67For2026Q3`.
- Commit: `feat(F03): pull_request_work_item table and the done statuses setting`

## Task 2. Reader of stored pull requests

A public reader in `source.github`: the latest `pull_request` records of a repository merged in a date range (UTC, inclusive), and one by repository and number.

- Files: `source/github/StoredPullRequests.java` (and its JPA or SQL query), `StoredPullRequestsTest.java`.
- **Test first:** `StoredPullRequestsTest.listsTheLatestVersionOfEachPullRequestMergedInTheRange` (a second version is returned, not the first; a PR merged the day after `to` is left out).
- Commit: `feat(F03): read stored pull requests by merge date and number`

## Task 3. One import service for a pull request by number

Move the per-number fetch, store and count out of `GitHubImportJob` into a public service in `source.github`; `GitHubImportJob` calls it. No change of behaviour (decision 61).

- Files: new `source/github/PullRequestImporter.java`, `GitHubImportJob.java`, new `PullRequestImporterTest.java`; existing `GitHubImportJobTest` stays green.
- **Test first:** `PullRequestImporterTest.importsAMergedPullRequestAndCountsEachRecordByKindAndOutcome`.
- Commit: `refactor(F03): one import service for a pull request, shared by both jobs`

## Task 4. OpenProject API client

`OpenProjectApi`: GET against `ei.openproject.api-url`, no login, generic User-Agent, one call at a time with the pause, retries on 5xx, timeouts and `429` (with `Retry-After`), `404` and `400` reported as their own outcomes, every answer parsed with `CanonicalJson.parse` and checked at the boundary. `OpenProjectProperties` validated at start. Test fixture `OpenProjectApiFixture` (mock server), like `GitHubApiFixture`; recorded answers for 81224 under `src/test/resources/openproject/`.

- Files: `source/openproject/OpenProjectApi.java`, `OpenProjectProperties.java`, `OpenProjectConfiguration.java`, `OpenProjectApiException.java`, `application.yml`; tests `OpenProjectApiTest.java`, `OpenProjectApiFixture.java`, `OpenProjectPropertiesTest.java`, recorded JSON files.
- **Test first:** `OpenProjectApiTest.retriesAServiceUnavailableAnswerAndThenReturnsTheBody`.
- Commit: `feat(F03): OpenProject API client with pause and retries`

## Task 5. References in a pull request

Finds work item references in a PR body (the three URL forms, key or number, host from the `openproject` data source) and in its title (`[KEY-n]`), keys in upper case, each with where it was found (decision 57).

- Files: `source/openproject/WorkPackageReferenceFinder.java`, `WorkPackageReference.java`, `WorkPackageReferenceFinderTest.java`.
- **Test first:** `WorkPackageReferenceFinderTest.findsKeysAndNumbersInEveryUrlFormAndABracketedKeyInTheTitle` (real bodies of the design's examples; `housekeeping/npm-11` in a branch gives nothing).
- Commit: `feat(F03): find work item references in a pull request`

## Task 6. Resolving references

Resolves references to work package numbers: collection calls of up to 100, a single fetch for each reference the answer did not match, one at a time after a `400`, `404` is not visible (decision 58).

- Files: `source/openproject/WorkPackageReferenceResolver.java`, `ResolvedReference.java`, `WorkPackageReferenceResolverTest.java`, recorded answers (old key, `400`, `404`).
- **Test first:** `WorkPackageReferenceResolverTest.resolvesTheNumberTheKeyAndAnOldKeyToTheSameWorkPackage` (81224, COMMS-1078, SE-694).
- Commit: `feat(F03): resolve work item references on the server`

## Task 7. Fetching work packages and what hangs on them

Fetches work packages by number in collection calls of up to 100 (all pages), the tree under a Feature (`ancestor` filter, all pages), the activities of a work package, its pull request list reduced to the fields of decision 69, and a project.

- Files: `source/openproject/WorkPackageFetcher.java`, `FetchedWorkPackage.java`, `PullRequestListReducer.java` (or a method of the fetcher), tests `WorkPackageFetcherTest.java`, `PullRequestListReducerTest.java`.
- **Test first:** `PullRequestListReducerTest.keepsOnlyTheStableFieldsAndMarksTheCopyReduced`; then `WorkPackageFetcherTest.readsEveryPageOfTheTreeUnderAFeature`.
- Commit: `feat(F03): fetch work packages, their activities, pull request lists and projects`

## Task 8. Storing work packages and projects

Stores a work package with its activities and pull request list in one transaction (record kinds and URLs of the design's table), stores a project and fills or updates its `project` row.

- Files: `source/openproject/OpenProjectRecordKind.java`, `WorkPackageRecorder.java`, `ProjectRecorder.java`, `Project.java` and `ProjectRepository.java` (entity for the existing `project` table), tests `WorkPackageRecorderTest.java`, `ProjectRecorderTest.java`.
- **Test first:** `WorkPackageRecorderTest.storesAWorkPackageWithItsActivitiesAndPullRequestListOrNothing` (a failing activity leaves no row).
- Commit: `feat(F03): store work packages, activities, pull request lists and projects`

## Task 9. Links and the work package reader

Writes the links of a pull request into `pull_request_work_item`, replacing its earlier rows in one transaction (decision 60). A public reader of stored work packages: by number, the links of a pull request, the nearest Feature at or above a work package, the status changes read from the activities, and whether a Feature is done in a quarter (decision 64).

- Files: `source/openproject/PullRequestWorkItemLink.java` (entity), `PullRequestWorkItemLinks.java`, `StoredWorkPackages.java`, `StatusChange.java`, tests `PullRequestWorkItemLinksTest.java`, `StoredWorkPackagesTest.java`.
- **Test first:** `PullRequestWorkItemLinksTest.aSecondWriteReplacesTheLinksOfThatPullRequestOnly`; then `StoredWorkPackagesTest.theNearestFeatureIsTheItemItselfOrItsNearestFeatureAncestor` and `StoredWorkPackagesTest.aFeatureIsDoneInTheQuarterWhenItsStatusChangedIntoADoneStatusInIt` ("tested" on 30 Sep is done in Q3, on 1 Oct is not; "rejected" is never done).
- Commit: `feat(F03): pull request links and the reader of stored work packages`

## Task 10. The job `openproject-import`

Arguments (design, "Starting the job"; a number that is not stored is a `400`) and steps 1 to 6: read the pull requests, find and resolve references, collect the work packages (referenced, ancestors, direct children, tree under each Feature), fetch and store each once, write the links including the OpenProject-side ones, count. Failures as in the design.

- Files: `source/openproject/OpenProjectImportArguments.java`, `OpenProjectImportJob.java`, `OpenProjectImportFailedException.java`, tests `OpenProjectImportArgumentsTest.java`, `OpenProjectImportJobTest.java`.
- **Test first:** `OpenProjectImportArgumentsTest.refusesARangeAndNumbersTogetherAndAMissingQuarter`; then `OpenProjectImportJobTest.linksEachPullRequestAndStoresTheWorkPackagesBehindIt`.
- Commit: `feat(F03): openproject-import job`

## Task 11. Completion for Features done in the quarter

Step 7: for each stored Feature done in the run's quarter, the merged pull requests of the repository in its pull request list and in those of the items under it that are not stored yet are imported through `PullRequestImporter`, then linked; rounds repeat until nothing is new (decisions 61, 68).

- Files: `source/openproject/FeatureCompletion.java` (or a step of the job), `OpenProjectImportJob.java`, `FeatureCompletionTest.java`.
- **Test first:** `FeatureCompletionTest.importsTheMissingMergedPullRequestsOfAFeatureDoneInTheQuarterOnly` (a Feature done in October brings nothing; an open pull request and one of another repository are skipped).
- Commit: `feat(F03): pull every pull request of Features done in the quarter`

## Task 12. End to end through the job endpoint

Starts `openproject-import` through `POST /api/jobs/openproject-import/runs` with recorded GitHub and OpenProject answers, waits for the run, checks rows and counts, runs it again.

- Files: `source/openproject/OpenProjectImportEndToEndTest.java`.
- **Test first:** `OpenProjectImportEndToEndTest.aSecondRunAddsNoRows`, then `anOldKeyAndTheNumberLinkToTheSameWorkPackage`.
- Commit: `test(F03): openproject-import end to end through the job endpoint`

## Verifying the whole feature

```
export JAVA_HOME=~/.jdks/temurin-25.0.5
cd backend && ./mvnw spotless:apply && ./mvnw verify
cd backend && ./mvnw spring-boot:run      # with the Compose database up
curl -s -X POST localhost:8080/api/jobs/openproject-import/runs -H 'Content-Type: application/json' \
  -d '{"repository":"opf/openproject","mergedFrom":"2026-07-01","mergedTo":"2026-07-14","quarter":"2026-Q3"}'
curl -s localhost:8080/api/jobs/runs/<id>
```

Then the five checks of the design's "How it will be tested": the resolved share (about 77%), the two id forms and old key on PRs 26057 and 26061 (after `github-import` with those numbers), a second run that adds no rows, the completion pull requests of two done Features listed for the user to check by hand, and CI green. Evidence goes into `notes.md`.
