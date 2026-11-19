# F03 OpenProject connector: notes

## Task 10: the job `openproject-import`

- The argument `quarter` is parsed and checked, but the job does not read it until task 11 (completion, step 7). `OpenProjectImportJob.importWorkItemsOf` is the seam: step 7 calls it again with the pull requests it imports and adds up the failures.
- The argument rules of the range and the numbers are those of `github-import`: `GitHubImportArguments` is now public and `parse(arguments, ownArguments)` lets a second job add its own names.
- Steps 4 and 5 live in `WorkPackageCollector` and `WorkPackageImporter`, so the job only orders the steps. A failing project call fails the run at once; a failing work package is counted in `work_packages_failed`.
- `WorkPackageFetcher` and `WorkPackageReferenceResolver` are Spring beans, built in `OpenProjectConfiguration`. The reference finder is built at the start of each run, because it needs the base URL from the `openproject` data source.
- `HrefNumbers` reads the number at the end of a link; `StoredWorkPackages` uses it too.
