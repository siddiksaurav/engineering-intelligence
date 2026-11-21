# F04 Identity: notes

## This build machine

- Java 25 is `/usr/lib/jvm/jdk-25-oracle-x64` (the system default), not `~/.jdks/temurin-25.0.5`. Use `export JAVA_HOME=/usr/lib/jvm/jdk-25-oracle-x64`.
- No `nvm`; `node` is v22 on the `PATH`.
- No `.env` yet (sessions may not write it, decision 54). The database was started with `docker compose --env-file .env.example up -d`, and the backend with `POSTGRES_PASSWORD=ei_local ./mvnw spring-boot:run`.
- The development database was restored from `~/Downloads/ei.dump` (decision 91): F02's runs 1 and 2. `openproject-import` run 3 on 21 Nov 2026 stored 320 work items and 133 linked PRs; its completion step failed for 109 PRs for lack of a `GITHUB_TOKEN`.

## Differences from the plan

- Task 1: the migration and its test were written together and then run, so the test was not seen failing first. Tests cover every behaviour of the design (12 in `IdentityProposalFunctionsTest`).
- Task 3: the account query and `findExternalIdsAuthoredBy` share one package-private native query interface, `StoredAccountRecords`. When one id has two logins, the record with the latest `fetched_at` (then highest id) gives the login.
- Tasks 4 and 6: the tests were written first; their red state was a compile error (classes missing), not a run.
- Task 4: `Persons.ensure(GitHubAccount)` returns `Result(personId, Outcome)` (`CREATED`, `LOGIN_UPDATED`, `UNCHANGED`) for the job's counts. `IdentitySource` holds the two data source keys. `PersonIdentity` has package-private getters `getDataSource` and `getExternalId` that nothing reads yet: Error Prone's UnusedVariable refuses the mapped fields without them.
- Task 5: the "Read by F04/F05, not yet by production code" comments on `StoredWorkPackages.find`, `linksOf` and `statusChanges` were removed: `OpenProjectCandidates` now calls them. Evidence URLs are `data_source.base_url` of `openproject` plus `/wp/{number}`.
- Task 6: when a person has two equal-strength pairs and one of those users is also wanted by another person, the person's own tie is settled first (no proposal). The design did not order these two rules.
- Task 7: `ProposalWriter` also deletes another person's OpenProject identity row for a user that moves to a new winner, so the unique key of `person_identity` holds whatever order persons are written in. `UNCHANGED` compares user, name, the three counts and the evidence (as parsed JSON). `IdentityProposal` has package-private getters only Error Prone needs.
- Task 8: a count `proposals_removed` was added for the `REMOVED` result. `proposals_unchanged` counts only a winner equal to the stored row; a person with a confirmed pair is not counted in `persons_without_proposal`. The bot list is read for `Quarter.of(LocalDate.now(clock))`. No test forces the failure-at-the-end path (it reuses `FailedItems`).
