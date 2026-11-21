# F04 Identity: design

State: approved on 2026-11-21, through the question brief

## Goal

After F04 every human GitHub account in the stored records is exactly one `person`, with its GitHub identity in `person_identity`. For each person the system proposes the OpenProject user behind them, shows the evidence it rests on, and lets the user confirm or reject the proposal. Later features find a person from a GitHub account or from an OpenProject user id. Bots never become persons.

## In scope / Out of scope

**In.**
- The job `identity-build`: reads the stored GitHub records, creates the missing persons and GitHub identities, and writes OpenProject proposals with their evidence.
- The table `identity_proposal` and the decisions on it (confirm, reject).
- Two database functions with which the user confirms or rejects a proposal from `psql` (decision 98).
- The lookups later features use: GitHub account to person, OpenProject user id to person.
- The readers of stored GitHub records by author (decision 62: built by their first user).

**Out.**
- `level`: stays empty (master plan F04 block).
- Organization e-mail as the identity (spec 5): the public data has none. The GitHub account stands in for it.
- Merging two GitHub accounts of one human: not done; see Edge cases.
- A screen for proposals: no web page in F04.
- Evidence items, and who gets credit for a review comment: F05. F04 only makes sure the person exists.
- Reading the assignee: unreliable on this data (`poc-plan.md` 1).

## Data and tables

### Rules from the spec and the PoC plan

- **Spec 5, "What happens to each record", "One identity per person."** "Every employee has one organization email, and that email is the person's identity in this system. OpenProject, DSI Central and the other internal systems already use it, so they need no mapping. The system keeps one small map: organization email to the person's outside identities, which are the GitHub or GitLab handle and any other email used in Git commits. An item with an unknown handle is stored but belongs to nobody until the handle is mapped. Unmapped handles are listed every week."
  The PoC has no organization e-mail (public data). The person is keyed by their GitHub account; the OpenProject user is a second identity that must be found, because OpenProject on the public server is not linked to GitHub.
- **Spec 5, source 1.** "Person: Git handle or commit email, looked up in the identity map."
- **Spec 5, source 2.** "Person: assignee and creator."
- **`poc-plan.md` 1, "Missing or weak".** "Assignee identifies the person / Assignee is cleared at each hand-over; only 21% of items have one. Use the PR author, the 'Developers' field and the status history."
- **`poc-plan.md` 3.4.** "`person`, `person_identity`: One person, many outside identities (GitHub login, OpenProject user id). `level` is nullable."
- **`poc-plan.md` 3.7 step 4.** "Identity: create persons from PR authors; propose the OpenProject user from the 'Developers' field."
- **Master plan 2.** "The person on every evidence item is the PR author. Code written with AI belongs to the person who pushed it. The AI-usage labels on PRs are ignored." "Bot accounts (dependabot, the CI account, the Copilot review bot) and an author's replies in their own PR never produce evidence items." "Real developer names are shown."
- **Decision 42.** Bot PRs and comments are stored like any other record. The bot list is the setting `github.bot_logins`, plus "GitHub type Bot is a bot"; F04 and F05 apply it.

### Tables that exist (F01, migration V1)

```sql
create table person (
    id           bigint generated always as identity primary key,
    display_name text not null,
    level        text check (level in ('L1', 'L2', 'L3')),
    created_at   timestamptz not null default now()
);

create table person_identity (
    id             bigint generated always as identity primary key,
    person_id      bigint not null references person (id),
    data_source_id bigint not null references data_source (id),
    external_id    text not null,
    username       text,
    constraint person_identity_external_uq unique (data_source_id, external_id)
);
```

`data_source` rows: `github` (id 1), `openproject` (id 2).

Setting `github.bot_logins` (V5, valid from 2026-Q2): `["dependabot", "openprojectci", "copilot-pull-request-reviewer", "github-actions", "github-advanced-security"]`.

### How F04 fills them

- **GitHub identity:** `person_identity` row with `data_source = github`, `external_id` = the account's GitHub `databaseId` as text (stable when a login is renamed), `username` = the login (decision 82).
- **OpenProject identity:** `person_identity` row with `data_source = openproject`, `external_id` = the OpenProject user id (`/api/v3/users/80250` gives `80250`), `username` = the user's name as OpenProject shows it ("Kabiru Mwenja"). Written by the job for the current proposal of a person (state `proposed` or `confirmed`) and removed when that proposal is rejected: a proposal counts until it is rejected (decision 95).
- **`person.display_name`:** the GitHub login, kept equal to it when the login changes (decision 96).
- **`person.level`:** stays null.

### New table `identity_proposal` (migration V7)

One row per (person, OpenProject user) proposed. Derived data, rewritten by the job, but a decision on it is kept.

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | bigint identity | |
| `person_id` | bigint, FK `person` | |
| `openproject_user_id` | bigint | The proposed OpenProject user |
| `openproject_user_name` | text | Their name, from the `title` of the user link |
| `supporting_items` | int | Linked work items whose "Developers" names this user |
| `status_change_items` | int | Linked work items whose status this user changed |
| `person_items` | int | Work items linked from the person's pull requests |
| `evidence` | jsonb | List of `{ "workPackage": 81224, "url": "https://community.openproject.org/wp/81224", "signal": "developers", "at": null }`; `signal` is `developers` or `status_change`; `at` is the activity time for a status change |
| `state` | text | `proposed`, `confirmed`, `rejected` |
| `proposed_at` | timestamptz | |
| `decided_at` | timestamptz, null | When confirmed or rejected |
| unique | `(person_id, openproject_user_id)` | |

The job replaces a person's `proposed` row (evidence, counts, or the whole row when another user now wins); it never changes a `confirmed` or `rejected` row (decision 85). At most one row per person is `proposed` or `confirmed`.

### Database functions (migration V7, decision 98)

- `confirm_identity_proposal(proposal_id bigint)`: a `proposed` row becomes `confirmed`, `decided_at = now()`. Any other state raises an error naming the id and its state.
- `reject_identity_proposal(proposal_id bigint)`: a `proposed` or `confirmed` row becomes `rejected`, `decided_at = now()`, and the person's OpenProject `person_identity` row for that user is deleted in the same statement. A `rejected` row or an unknown id raises an error naming the id. The next `identity-build` run proposes the person's next candidate.

Used from the machine:

```
docker compose exec postgres psql -U ei -d ei -c "select reject_identity_proposal(7);"
```

### Facts about the real data

Checked 21 Nov 2026 on the dump of the F02 run (215 merged PRs of 1 to 14 Jul 2026, restored from the earlier build machine, decision 91) and on `community.openproject.org`.

- **Authors.** 23 human PR authors plus `dependabot` (GitHub type `Bot`, 35 PRs) and `openprojectci` (type `User`, 8 PRs; on the bot list). Every author has `login`, `__typename` and `databaseId`; no PR has a missing author. The author object carries no name.
- **Reviewers and commenters who wrote no PR.** 7 human accounts review or comment without a PR of their own in the two weeks: `dombesz` (14 reviews, 23 review comments), `cbliard` (3 reviews, 2 review comments, 1 comment), `ba1ash`, `machisuji`, `wielinde`, `isugic`, `cguiot-OP`. Bots among reviewers and commenters: `copilot-pull-request-reviewer` (63 reviews, 94 review comments), `github-actions` (295 comments), `github-advanced-security`. All review, review comment and conversation comment authors carry `login`, `__typename`, `databaseId`.
- **"Developers" field.** Custom field `customField270`, type list of users, named "Developers" in the schema (`/api/v3/work_packages/schemas/1395-6`). On a work package it is in `_links.customField270` as `[{"title": "Kabiru Mwenja", "href": "/api/v3/users/80250"}]`. Example: Feature 81224 has Developers = Kabiru Mwenja (user 80250); the GitHub author of its PRs is `akabiru`.
- **Status history.** Each activity's `_links.user` names who made the change (`href` and `title`). F03's `StoredWorkPackages.statusChanges(number)` gives `StatusChange(status, at, userId)`.
- **OpenProject users.** `/api/v3/users/80250` answers without login with `name` "Kabiru Mwenja". F04 does not call it: the name is in the link titles already stored.
- **Groups in "Developers".** The field can hold a team instead of a person: `{"href": "/api/v3/groups/99377", "title": "OP Dev Sirius Team"}`. Seen twice in 111 work items.
- **Status-change users have no name.** An activity's `_links.user` is `{"href": "/api/v3/users/83297"}` with no `title`; only the Developers field (and author, assignee) links carry the name.

### Matching evidence on the real data

Checked 21 Nov 2026 on the work items stored so far by `openproject-import` run 3 (111 work items, 88 of them with "Developers" filled), pairing each work item's "Developers" users and status-change users with the GitHub logins of the merged PRs OpenProject lists on it. Script: the session's `pairs.sql`, summarized here.

- **"Developers" names the author's own user on most authors.** `oliverguenther` 11 of 12 items Oliver Günther (9177); `mrmir` 11 of 12 Mir Bhatia (78274); `myabc` 7 of 9 Alexander Coles (99227); `klaustopher` 7 of 7 Klaus Zanders (80745); `thykel` 6 of 9 Tom Hykel (99893); `brunopagno` 5 of 5 Bruno Pagno; `bsatarnejad` 5 of 5 Behrokh Satarnejad (64880); `akabiru` 3 of 3 Kabiru Mwenja (80250); `EinLama` 4 of 4 Tobias Dillmann (99090); `ulferts` 3 of 4 Jens Ulferts (87); `judithroth` 3 of 9 Judith Roth (98858).
- **Ties.** `dfriquet`: David Friquet 1, Oliver Günther 1. `toy`: Alexander Coles 1, user 95889 1. `HDinger`: Henriette Darge 2, Behrokh Satarnejad 2.
- **Status changes alone are ambiguous.** The author's user is usually among those who change status, but so are QA and leads: `bsatarnejad` 64880 on 5 items and 96697 on 5; `EinLama` 99090 on 4 and 77197 on 4; `ehassan01` 18334 and 99786 on 5 each. As a tie-breaker it helps: `dfriquet` 99894 (David Friquet) 2 against 9177 1; `toy` 95889 2 against 99227 1.
- **No "Developers" value at all** on the linked items of `corinnaguenther`, `ehassan01`, `MayaBerd`, `Kharonus`; their status-change users tie or are shared with others (18334 appears for both `ehassan01` and `MayaBerd`).
- **Full import (run 3 ended 21 Nov 2026, 15:52 UTC):** 320 work items, 5,178 activities, 133 of 215 PRs with a resolved link (418 resolved links, 20 `not_visible`). The run is marked failed only because its completion step could not import 109 PRs without a `GITHUB_TOKEN`; everything else is stored. On it, the top "Developers" user per author: `oliverguenther` Oliver Günther 24 of 29, `HDinger` Henriette Darge 24 of 27, `mrmir` Mir Bhatia 23 of 25, `brunopagno` Bruno Pagno 20 of 20, `klaustopher` Klaus Zanders 19 of 22, `dfriquet` David Friquet 17 of 20, `thykel` Tom Hykel 16 of 19, `akabiru` Kabiru Mwenja 13 of 14, `myabc` Alexander Coles 13 of 22, `toy` Ivan Kuchin 8 of 11, `bsatarnejad` Behrokh Satarnejad 8 of 8, `EinLama` Tobias Dillmann 7 of 7, `judithroth` Judith Roth 4 of 17, `Kharonus` Eric Schubert 3, `ulferts` Jens Ulferts 3, `NobodysNightmare` Jan Sandbrink 3, `shiroginne` Yauheni Suhakou 2, `as-op` Andrej Sandorf 1, `tiroessler` Tizian Rößler 1. Two wrong-looking tops: `ihordubas99` Judith Roth 1 (Q7 gives Judith to `judithroth`), `dombesz` tie Attila Dombi 1 / Alexander Coles 1 (Q7 gives Alexander to `myabc`).

## Main flows

### Job `identity-build`

Started like every job (decision 44):

```
curl -s -X POST localhost:8080/api/jobs/identity-build/runs -H 'Content-Type: application/json' -d '{}'
```

No arguments (decision 83): identity is not tied to a window; the job reads every stored record. Run after `github-import` and `openproject-import`; running it again is safe.

1. **Read the GitHub accounts** from the latest versions of the stored `pull_request`, `review`, `review_comment` and `pull_request_comment` records, each as (`databaseId`, `login`, `__typename`). Every human account found there becomes a person, whether it wrote a PR or only reviews and comments (decision 92). When one `databaseId` appears with two logins (a rename between fetches), the login of the most recently fetched record wins.
2. **Drop bots:** an account whose `__typename` is `Bot` or whose login is in `github.bot_logins` (decision 84). Counted `bots_skipped` (distinct accounts).
3. **Persons:** for each remaining account with no GitHub identity, create a `person` and its `person_identity`. An account whose identity exists but whose login changed gets its `username` updated. Counted `persons_created`, `logins_updated`.
4. **Candidates:** for each person, the work items linked to their pull requests (`pull_request_work_item`, state `resolved`, decision 88). A candidate is every OpenProject *user* (href `/api/v3/users/{id}`, groups ignored, decision 86) named in "Developers" (`_links.customField270`) on at least one of those items. Per candidate: `supporting_items` = items whose "Developers" names them; `status_change_items` = items whose status they changed (`StoredWorkPackages.statusChanges`, `userId`). A user only seen in status changes is not a candidate (decision 93). Pairs the person rejected earlier are dropped.
5. **Assignment** (decisions 93, 94, 97), over all persons together:
   - An OpenProject user in a `confirmed` proposal is taken by that person; that person keeps it and gets nothing else.
   - The remaining (person, candidate) pairs are ranked by `supporting_items`, then `status_change_items`, both high first. They are taken in that order: a pair is assigned when its person has nothing yet and its user is not taken.
   - A person's best untaken candidate must be unique: if their next two untaken candidates are equal on both counts, the person gets no proposal (a tie, decision 93).
   - Two persons equal on both counts for the same user: neither gets it, nor does any weaker person (decision 99); each goes on to their next candidate.
   - One supporting item is enough (decision 94).
6. **Write:** per person, in one transaction: replace the person's `proposed` row with the assigned one (or delete it when nothing is assigned), and keep exactly one OpenProject `person_identity` row for the person's current proposal. Counted `proposals_written`, `proposals_unchanged`, `persons_without_proposal`.

Steps 1 to 3 and 6 run one person per transaction, so a failure leaves earlier persons in place; the run fails at the end naming the first 20 failed accounts (decision 90).

### Confirming or rejecting a proposal

The session lists the proposals with their evidence for the user (a SQL query on `identity_proposal`). The user confirms or rejects with the database functions above (decision 98). A run of `identity-build` after a reject proposes the next candidate.

### Uses (from earlier features)

```java
package com.dsinnovators.ei.source;
public interface RawRecordStore {
    Optional<RawRecord> findLatest(String dataSourceKey, String recordKind, String externalId);
    List<RawRecord> findLatestByKind(String dataSourceKey, String recordKind);
}
// RawRecord: getExternalId(), JsonNode getPayload(), ...

package com.dsinnovators.ei.source.openproject;
public class StoredWorkPackages {
    public Optional<RawRecord> find(long number);
    public List<PullRequestWorkItemLink> linksOf(String pullRequestExternalId);
    public Optional<Long> nearestFeature(long number);
    public List<StatusChange> statusChanges(long number);
}
public record StatusChange(String status, Instant at, Long userId) {}
public class PullRequestWorkItemLink { getPullRequestExternalId(); Long getWorkPackageId(); boolean isVisible(); }

package com.dsinnovators.ei.job;
public interface Job { String name(); void checkArguments(JobArguments a); void run(JobArguments a, JobCounts c) throws Exception; }

package com.dsinnovators.ei.setting;
public interface SettingService { List<String> getTextList(String key, Quarter quarter); }
```

GitHub record kinds (`GitHubRecordKind`): `pull_request`, `review`, `review_comment`, `pull_request_comment`; author at `payload.author { login, __typename, databaseId }`. Pull request external id `opf/openproject#26057`.

The bot list is a setting read for a quarter; the job reads it for the quarter of the run's start day (decision 84).

### Gives (to later features)

In package `identity` (exact class names in `plan.md`):
- `PersonDirectory.personOfGitHubAccount(long databaseId)` → `Optional<Long>` person id.
- `PersonDirectory.personOfOpenProjectUser(long userId)` → `Optional<Long>` person id, read from `person_identity` (a proposed or confirmed match, decision 95).
- `BotAccounts.isBot(String login, String typename, Quarter quarter)`: the one bot rule, for F05.
- Tables `person`, `person_identity`, `identity_proposal`.
- In `source.github`: a reader of stored GitHub records by author: the accounts of the stored records, and the pull requests of one account.

## Edge cases

| Case | What happens |
| --- | --- |
| The job runs twice | No new person, no new identity; proposals refreshed with the same content; decisions kept |
| A login renamed on GitHub (same `databaseId`) | Same person; `username` updated |
| A bot typed `User` (`openprojectci`) | Skipped through the bot list |
| A Copilot coding-agent PR (author typed `Bot`) | Skipped like any bot. None in the two weeks |
| One human with two GitHub accounts | Two persons. Not merged in the PoC; the stronger claim gets the shared OpenProject user (decision 97) |
| A person with no linked work item, or none with "Developers" filled | No proposal; counted `persons_without_proposal` |
| A candidate that was confirmed for another person | Never proposed to this person |
| A proposed user that another person now claims more strongly | Moves to the stronger person on the next run; this person gets their next candidate |
| A proposal rejected | Its OpenProject identity is removed at once; never proposed again for that person; the next run proposes the next candidate |
| A proposal confirmed | Kept as is; a later run does not change it |
| A new GitHub account in a later import | Becomes a person on the next run |
| The bot list changes after a bot became a person | Not possible today (the list is checked before a person is made); a person is never deleted |

## How it will be tested

Unit and database tests (Testcontainers, `DatabaseTest`), with stored records written through `RawRecordStore` and work packages from F03's recorded answers. No test calls GitHub or OpenProject.

Master plan "Done when":
- **"Every human PR author in the test range is exactly one person."** Database test: two runs over authors including a renamed login give one person per `databaseId`. Real data: after the job on the dump, `select count(*) from person` equals the distinct human accounts counted by separate SQL, and no `databaseId` has two persons.
- **"No bot is a person."** Test with `dependabot` (type Bot), `openprojectci` (type User, on the list) and `copilot-pull-request-reviewer`: no person. Real data: SQL join of `person_identity.username` with the bot list gives 0.
- **"Each proposed OpenProject match shows the evidence it rests on."** Test: a proposal holds one evidence entry per supporting work item with its URL and signal. Real data: every `identity_proposal` row has a non-empty `evidence`; the session lists the proposals with their links and the user checks them by hand ("waiting for the user").
- **"A match can be confirmed or rejected by hand."** Database tests of `confirm_identity_proposal` and `reject_identity_proposal` (states, `decided_at`, the OpenProject identity removed, errors on a wrong state), and that a rejected pair is not proposed again on a second run while the next candidate is.
- **The rule.** Unit tests of the assignment: most "Developers" items wins; a tie broken by status changes; a full tie gives no proposal; a user seen only in status changes is never proposed; groups ignored; the stronger of two persons gets a shared user and the other their next candidate; a confirmed user stays with its person.
- **Real data.** After the job on this machine's database, the session lists every proposal with its top evidence links for the user to check ("waiting for the user"), and compares them with the figures in "Matching evidence".

## Decisions

Made in this design (session): 82 GitHub identity keyed by `databaseId`; 83 job `identity-build` with no arguments; 84 one bot rule; 85 table `identity_proposal`, decisions kept; 86 groups ignored; 87 names from the stored link titles; 88 only the linked work items; 89 readers by author in `source.github`; 90 one transaction per person; 91 the restored dump.

From the question brief (user, 21 Nov 2026): 92 every human account becomes a person; 93 "Developers" first, ties broken by status changes; 94 propose the best candidate even from one item; 95 a proposal counts until rejected; 96 the display name is the GitHub login; 97 the stronger claim wins a shared user; 98 confirm and reject through SQL (two database functions).
