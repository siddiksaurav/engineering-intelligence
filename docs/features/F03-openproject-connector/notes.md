# F03 OpenProject connector: notes

## Differences from the plan

Recorded by the coordinator of `/build F03` as each task reported. The reason follows each difference.

- **Task 2.** The stored pull request reader uses a package-private `MergedPullRequestRecords` with one native query, the "JPA or SQL query" of the plan. JPQL cannot filter on the `mergedAt` field inside the jsonb payload, and `RawRecordRepository` is package-private in another package. The reader returns `RawRecord`.
- **Task 4.** A `404` and a `400` each have their own exception, `OpenProjectNotFoundException` and `OpenProjectBadRequestException`, both subclasses of `OpenProjectApiException`, so callers can tell the two outcomes apart. `Sleeper` was first copied into `source.openproject`; it is now one public interface in `common`, shared by both API clients.
- **Task 5.** `WorkPackageReferenceFinder` takes the base URI of the `openproject` data source as a constructor value. The job builds it at the start of each run from the `data_source` row.
- **Task 6.** The recorded collection, `400` and `404` answers are small hand-made JSON files shaped like the server's answers, not live recordings: the real collection answers are very large. The old-key fetch reuses the real `work-package-81224.json`. `resolve` takes reference strings; the caller keeps where each was found.
- **Task 7.** `WorkPackageCollectionPath` is the one place that writes the `id` and `ancestor` filters; the resolver uses it too. Child 83452 is not recorded, because no test needs it. The tree pages and project 1395 are hand-made; the pull request list of 81224 is a real answer cut to 2 entries.
- **Task 8.** `OpenProjectSource` reads the `openproject` data source (key, base URL) once for both recorders. `StoredRecord` exists in each source package because it is typed by that source's record kind. The first rollback test passed only through input validation; `WorkPackageRecorderRollbackTest` now makes a spied `RawRecordStore` fail after two rows were written, and it goes red without `@Transactional`.
- **Task 9.** Status changes are read from the `html` form of an activity detail (decision 70). "Status set to" was missed at first and added in a fix. `FoundIn` gained `OPENPROJECT`; the stored `found_in` value is the lower-case enum name.
- **Task 10.** Steps 4 and 5 live in `WorkPackageCollector` and `WorkPackageImporter`, so the job only orders the steps; `HrefNumbers` reads the number at the end of a link. `GitHubImportArguments` is public with `parse(arguments, ownArguments)`, so both jobs share the range and number rules. A failing project call fails the run at once (decision 71).
- **Task 11.** `FeatureCompletion` is its own class. `FeatureCompletionTest` and the job tests use a Mockito mock of `PullRequestImporter`, to keep those tests about the rounds; the end-to-end test of task 12 runs step 7 against the real importer and the GitHub mock server.
- **Task 12.** A third test, `aFeatureDoneInTheQuarterGetsItsMissingPullRequestThroughTheRealImporter`, was added for step 7. `GitHubApiFixture` is public, and the public test helper `RecordedGitHub` in `source/github` replaces the nested configuration of `GitHubImportEndToEndTest`. The starting pull requests are stored through `RawRecordStore` directly.
- **Review round 1.** One `ImportRun` holds what a run has resolved, requested, collected and stored, so the completion rounds do not fetch a work package again. Links are written once at the end of the run, for every pull request of the run, from all work packages stored in the run. `OpenProjectAnswers` checks `id`, `_links`, `_type` and `_embedded.elements` of every answer. Shared helpers replace copies: `WorkPackageType.isFeature`, `StoreResult.Outcome.countName`, `job.FailedItems.named` (the first 20 failures, used by both jobs).
- **Test first.** In tasks 4 and 5 the implementer wrote test and code together and did not see the test fail first; later tasks did.

## Task 10: the job `openproject-import`

- The argument `quarter` is parsed and checked; step 7 reads it.
- The argument rules of the range and the numbers are those of `github-import`: `GitHubImportArguments` is public and `parse(arguments, ownArguments)` lets a second job add its own names.
- Steps 4 and 5 live in `WorkPackageCollector` and `WorkPackageImporter`, so the job only orders the steps. A failing project call fails the run at once; a failing work package is counted in `work_packages_failed`.
- `WorkPackageFetcher` and `WorkPackageReferenceResolver` are Spring beans, built in `OpenProjectConfiguration`. The reference finder is built at the start of each run, because it needs the base URL from the `openproject` data source.
- `HrefNumbers` reads the number at the end of a link; `StoredWorkPackages` uses it too.

## Task 11: completion for Features done in the quarter

- `FeatureCompletion` runs step 7. Each round it reads the stored Features done in the quarter (`StoredWorkPackages.featuresDoneIn`), the work packages under each (`numbersUnder`, by the ancestors link) and the pull requests OpenProject lists on them (`listedPullRequests`), imports the merged ones of the run's repository that are not stored, and gives them to the job's work item import. It stops when a round stores no new pull request. A pull request is asked for once; one that fails is counted in `pull_requests_failed` and named when the run fails.
- Counts: `features_done_in_quarter`, `pull_requests_completed`, `pull_requests_failed`, and F02's counts for the imported pull requests.
- `FeatureCompletionTest` uses a Mockito mock of `PullRequestImporter` that stores a `pull_request` record, so its tests are about the rounds only. The real importer with `GitHubApiFixture` is exercised by `OpenProjectImportEndToEndTest`.

## What a later feature should know

- The activities of a work package are found by a jsonb path on `raw_record` (`WorkPackageActivityRecords`) without an index. Fine for the PoC; a feature that reads many work packages may need an expression index.
- Status names on community.openproject.org are lower case ("in development", "review approved"); `openproject.done_statuses` matches them exactly (decision 70).

## Kept for later features

Used by tests only for now; each carries a comment naming the feature that reads it.

| Code | Read by |
| --- | --- |
| `StoredWorkPackages.find` | F05 |
| `StoredWorkPackages.linksOf` | F05 |
| `StoredWorkPackages.nearestFeature` | F05 |
| `StoredWorkPackages.statusChanges` (production code reads it through `isDoneIn`) | F04, F05 |
| `PullRequestWorkItemLink.getPullRequestExternalId`, `getReference`, `getLinkedAt` | F05 |

The `Project` entity is gone: no feature reads a project row through an entity (the repository stands in for the project), so `ProjectRepository` upserts the row with one native query. A feature that needs to read projects adds the entity then.
