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

## Verification

Full suites on 21 Nov 2026: backend `./mvnw verify` 433 tests, 0 failures, 12 skipped (disabled architecture tests of F05, F06, F10, F11), build green. Web: format, lint, type check, 27 tests, build all green.

Real data, this machine's database (dump of F02 plus `openproject-import` run 3), `identity-build` run 4 on 21 Nov 2026: `accounts_read` 35, `bots_skipped` 5, `persons_created` 30, `proposals_written` 19, `persons_without_proposal` 11.

- **Every human PR author is exactly one person: passed.** Distinct human `databaseId`s over stored PRs, reviews, review comments and comments, by separate SQL (bot list and type `Bot` removed): 30. `person`: 30; GitHub identities: 30; external ids with two persons: 0.
- **No bot is a person: passed.** Identities whose username is on `github.bot_logins`: 0; the 5 bots (`dependabot`, `openprojectci`, `copilot-pull-request-reviewer`, `github-actions`, `github-advanced-security`) skipped.
- **Each proposal shows its evidence: passed.** 19 proposals, 0 with empty evidence; 19 OpenProject identities (one per proposal, decision 95).
- **Confirm or reject by hand: passed by tests** (`IdentityProposalFunctionsTest`, 12; `IdentityBuildJobTest` second run after a reject). On real data: waiting for the user's confirm or reject.
- **After the review fixes:** backend 435 tests, 0 failures, 12 skipped. Run 6 rewrote the 19 proposals once (evidence signals now `developers` and `status_change`, decision of the review); the proposal ids below are from run 6.
- **A second run changes nothing: passed.** Run 5: `proposals_unchanged` 19, no person or proposal created.
- **Persons without a proposal (11):** 7 reviewers or commenters with no stored PR (`ba1ash`, `cbliard`, `cguiot-OP`, `dombesz`, `isugic`, `machisuji`, `wielinde`); `corinnaguenther`, `ehassan01`, `MayaBerd` (no "Developers" on their linked items); `ihordubas99` (only Judith Roth, who goes to `judithroth`, decision 97).
- Counts are lower than the design's "full import" figures (for example `oliverguenther` 14 of 16, not 24 of 29) because the job reads stored PRs only; the design's figures came from OpenProject's PR lists, which include the 109 PRs the import could not fetch without a token.

**Waiting for the user: check the 19 proposals** (GitHub profile, one evidence work item):
- 20. myabc → Alexander Coles (11 of 19 items): https://github.com/myabc, https://community.openproject.org/wp/76684
- 21. toy → Ivan Kuchin (4 of 5 items): https://github.com/toy, https://community.openproject.org/wp/71971
- 22. shiroginne → Yauheni Suhakou (2 of 2 items): https://github.com/shiroginne, https://community.openproject.org/wp/76455
- 23. NobodysNightmare → Jan Sandbrink (3 of 3 items): https://github.com/NobodysNightmare, https://community.openproject.org/wp/75411
- 24. oliverguenther → Oliver Günther (14 of 16 items): https://github.com/oliverguenther, https://community.openproject.org/wp/75729
- 25. EinLama → Tobias Dillmann (4 of 4 items): https://github.com/EinLama, https://community.openproject.org/wp/76559
- 26. klaustopher → Klaus Zanders (10 of 11 items): https://github.com/klaustopher, https://community.openproject.org/wp/75536
- 27. ulferts → Jens Ulferts (3 of 4 items): https://github.com/ulferts, https://community.openproject.org/wp/76741
- 28. dfriquet → David Friquet (7 of 9 items): https://github.com/dfriquet, https://community.openproject.org/wp/75179
- 29. brunopagno → Bruno Pagno (6 of 6 items): https://github.com/brunopagno, https://community.openproject.org/wp/76170
- 30. thykel → Tom Hykel (10 of 12 items): https://github.com/thykel, https://community.openproject.org/wp/75356
- 31. HDinger → Henriette Darge (5 of 6 items): https://github.com/HDinger, https://community.openproject.org/wp/76579
- 32. mrmir → Mir Bhatia (12 of 13 items): https://github.com/mrmir, https://community.openproject.org/wp/76226
- 33. judithroth → Judith Roth (3 of 13 items): https://github.com/judithroth, https://community.openproject.org/wp/75977
- 34. akabiru → Kabiru Mwenja (5 of 5 items): https://github.com/akabiru, https://community.openproject.org/wp/76457
- 35. Kharonus → Eric Schubert (2 of 3 items): https://github.com/Kharonus, https://community.openproject.org/wp/76401
- 36. bsatarnejad → Behrokh Satarnejad (7 of 7 items): https://github.com/bsatarnejad, https://community.openproject.org/wp/76637
- 37. as-op → Andrej Sandorf (1 of 1 items): https://github.com/as-op, https://community.openproject.org/wp/76567
- 38. tiroessler → Tizian Rößler (1 of 1 items): https://github.com/tiroessler, https://community.openproject.org/wp/75421

## Follow-ups (from the review, not fixed in F04)

All under `backend/src/main/java/com/dsinnovators/ei/identity/` unless named.
- `IdentityBuildJob`, `ProposalWriter.replace`: a person whose candidate step fails, or who no longer appears in the stored accounts, keeps a `proposed` row whose user may be won by another person; `replace` then deletes the old holder's OpenProject identity, leaving a proposed row without one. Low risk (only after a failure or a change of author). Fix: treat users of proposed rows not rewritten in the run as taken.
- `IdentityBuildJobTest`: types `dependabot` as `User` and `openprojectci` as `Bot`, the reverse of the real data; both bot rules are covered, the real cases are not shown.
- No job-level test of `proposals_removed`, nor of a person with a confirmed pair left out of `persons_without_proposal`.
- The data-source lookup with its "is not in the database" error is written three times (`Persons.create`, `ProposalWriter.openProject`, `OpenProjectCandidates.workItemUrl`); `workItemUrl` queries `data_source` once per evidence entry.
- `OpenProjectCandidates` parses OpenProject payloads (`_links.customField270`, href numbers) in `identity`; a `developers(number)` reader on `source.openproject.StoredWorkPackages` and `HrefNumbers` would keep the row layout in its package (decisions 62, 89).
- Getters that exist only for Error Prone's UnusedVariable: `PersonIdentity.getDataSource`, `getExternalId`, `IdentityProposal.getPersonId`, `getState`.
- More visible than needed: `Persons`, `ProposalWriter`, `OpenProjectCandidates`, `Candidate`, `EvidenceEntry`, `Person`, `PersonIdentity` and nested `Outcome`, `Result` are `public`; F05 needs only `PersonDirectory` and `BotAccounts`.
- `BotAccounts.isBot` reads the setting once per account; once per run would do.
