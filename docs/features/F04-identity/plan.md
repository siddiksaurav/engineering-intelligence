# F04 Identity: plan

**Spec:** `docs/features/F04-identity/design.md` (approved 21 Nov 2026). Its tables, flows, edge cases and decisions 82 to 98 carry the detail; this plan gives the order.

Backend packages: `source.github` (reader of stored GitHub records by author), `identity` (everything else, new). Tests use records written through `RawRecordStore` and F03's recorded OpenProject answers; no test calls GitHub or OpenProject.

## Batch 1: tasks 1 to 3 (schema, bot rule, reader)

### Task 1. Table `identity_proposal` and the two functions

Migration V7: table `identity_proposal` (design, "New table"), unique `(person_id, openproject_user_id)`, state check, a partial unique index allowing at most one `proposed` or `confirmed` row per person; functions `confirm_identity_proposal(bigint)` and `reject_identity_proposal(bigint)` (design, "Database functions"). `DatabaseTest` also empties `identity_proposal`; `SchemaTest` lists the new table.

- Files: `backend/src/main/resources/db/migration/V7__identity_proposal.sql`, `DatabaseTest.java`, `SchemaTest.java`, new `identity/IdentityProposalFunctionsTest.java`.
- **Test first:** `IdentityProposalFunctionsTest.rejectMarksTheProposalRejectedAndRemovesTheOpenProjectIdentity`; then confirm from `proposed`, and errors naming the id for a confirm of a rejected row and for an unknown id.
- Commit: `feat(F04): identity_proposal table with confirm and reject functions`

### Task 2. The bot rule

`identity.BotAccounts.isBot(String login, String typename, Quarter quarter)`: `Bot` type or login in `github.bot_logins` (decision 84). Public, for F05.

- Files: new `identity/BotAccounts.java`, `identity/BotAccountsTest.java`.
- **Test first:** `BotAccountsTest.anAccountOnTheBotListIsABotEvenWhenGitHubTypesItUser` (`openprojectci`); also `dependabot` typed `Bot`, a human `User` not a bot.
- Commit: `feat(F04): one rule for GitHub bot accounts`

### Task 3. Reader of stored GitHub records by author

In `source.github`, public `StoredGitHubAccounts`: `accounts()` gives every distinct account (`databaseId`, `login`, `typename`) in the latest versions of `pull_request`, `review`, `review_comment`, `pull_request_comment`, with the login of the most recently fetched record when one id has two logins; `pullRequestsOf(long databaseId)` gives the external ids of the latest stored pull requests that account wrote (decision 89).

- Files: new `source/github/StoredGitHubAccounts.java`, `source/github/GitHubAccount.java` (record), a native query interface, `StoredGitHubAccountsTest.java`.
- **Test first:** `StoredGitHubAccountsTest.findsEachAccountOnceAcrossPullRequestsReviewsAndComments` (a reviewer with no PR is found; a renamed login gives the newer login).
- Commit: `feat(F04): read stored GitHub accounts and their pull requests`

## Batch 2: tasks 4 to 6 (persons, candidates, assignment)

### Task 4. Persons and their GitHub identities

Entities `Person`, `PersonIdentity` (JPA), and `identity.Persons` that creates a person with its GitHub identity for a new account, updates `username` and `display_name` when the login changed (decisions 82, 96), and does nothing for a known unchanged account. Public `PersonDirectory` with `personOfGitHubAccount(long databaseId)` and `personOfOpenProjectUser(long userId)`.

- Files: new `identity/Person.java`, `identity/PersonIdentity.java`, `identity/PersonRepository.java`, `identity/PersonIdentityRepository.java`, `identity/Persons.java`, `identity/PersonDirectory.java`, tests `PersonsTest.java`, `PersonDirectoryTest.java`.
- **Test first:** `PersonsTest.aRenamedLoginKeepsThePersonAndUpdatesTheUsernameAndDisplayName`; then a new account creates one person and one identity, and a second call creates nothing.
- Commit: `feat(F04): persons with their GitHub identity, and the person lookups`

### Task 5. Candidates of a person

`identity.OpenProjectCandidates.of(List<String> pullRequestExternalIds)`: from the resolved links of those PRs, the users (not groups) in "Developers" with `supporting_items`, `status_change_items`, name and the evidence entries (design, "Candidates" and `evidence` shape). Reads `StoredWorkPackages` (`linksOf`, `find`, `statusChanges`).

- Files: new `identity/OpenProjectCandidates.java`, `identity/Candidate.java` (record), `identity/EvidenceEntry.java` (record), `OpenProjectCandidatesTest.java`.
- **Test first:** `OpenProjectCandidatesTest.countsDevelopersAndStatusChangesPerUserAndIgnoresGroups` (a status-only user is not a candidate; a work item linked twice counts once).
- Commit: `feat(F04): OpenProject candidates of a person from Developers and status changes`

### Task 6. Assignment

`identity.ProposalAssignment`: pure logic. Input: candidates per person, rejected pairs, confirmed (person, user) pairs. Output: at most one assigned candidate per person, by the rules of design step 5 (decisions 93, 94, 97).

- Files: new `identity/ProposalAssignment.java`, `ProposalAssignmentTest.java`.
- **Test first:** `ProposalAssignmentTest.theStrongerPersonGetsASharedUserAndTheOtherGetsTheirNextCandidate`; then: tie broken by status changes; full tie gives none; rejected pair skipped; a confirmed user is never given to another person; one item is enough; two persons equal for one user both go on.
- Commit: `feat(F04): assign one OpenProject proposal per person`

## Batch 3: tasks 7 and 8 (store, job)

### Task 7. Writing proposals

Entity `IdentityProposal` and `identity.ProposalWriter.write(personId, Optional<Candidate>)`: in one transaction, replaces the person's `proposed` row (or deletes it), leaves `confirmed` and `rejected` rows untouched, and keeps exactly one OpenProject `person_identity` row for the current proposal (decision 95). Reports `WRITTEN`, `UNCHANGED` or `REMOVED`.

- Files: new `identity/IdentityProposal.java`, `identity/IdentityProposalRepository.java`, `identity/ProposalWriter.java`, `ProposalWriterTest.java`.
- **Test first:** `ProposalWriterTest.aNewWinnerReplacesTheProposedRowAndItsOpenProjectIdentity`; then same candidate gives `UNCHANGED`; a confirmed row is kept untouched.
- Commit: `feat(F04): write proposals and the OpenProject identity`

### Task 8. Job `identity-build`

`identity.IdentityBuildJob` (name `identity-build`, no arguments; any argument is refused): accounts, bots skipped, persons, candidates, assignment, write; counts `accounts_read`, `bots_skipped`, `persons_created`, `logins_updated`, `proposals_written`, `proposals_unchanged`, `persons_without_proposal`, `accounts_failed`; one person per transaction; fails at the end naming the first 20 failed accounts (decision 90). `CLAUDE.md` commands gain the job and the two SQL calls.

- Files: new `identity/IdentityBuildJob.java`, `IdentityBuildJobTest.java` (database, end to end through `JobEndpointClient`), `CLAUDE.md`.
- **Test first:** `IdentityBuildJobTest.twoRunsGiveOnePersonPerHumanAccountAndNoBotAndKeepARejectedPairOut` (author, reviewer-only, `dependabot`, `openprojectci`; a rejected proposal and the next candidate proposed on the second run).
- Commit: `feat(F04): identity-build job`

## Verify the whole feature

```
cd backend && ./mvnw spotless:apply && ./mvnw verify
cd web && npm run format:check && npm run lint && npm run typecheck && npm test && npm run build
```
