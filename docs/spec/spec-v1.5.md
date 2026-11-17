# Engineering Intelligence — Developer Performance Evaluation: Spec (Version 1.5)

Nov 11, 2026 · @Claude Sub

This spec describes a system that measures the performance of a Software Engineer from the work they already leave behind in Git, OpenProject and a few other systems. It is written to be read from top to bottom. Each section builds on the one before it, and a developer who has never seen the project should be able to implement the system from this document alone.

**What is new in Version 1.5.** Section 15 is added. It describes how developers on client-owned projects are scored, where DSi cannot read the client's code. Sections 1 to 14 are the same as in Version 1.

## 1. Background

DSi wants to measure the performance of its developers from evidence, not from memory or impression. Today a developer's performance is judged mostly by what their lead remembers. Good work that nobody talks about is easy to miss.

This system is the first part of a larger initiative called Engineering Intelligence. The first business goal of that initiative is to automate performance measurement for developers. Other roles such as QA, Project Manager and Business Analyst will come later and are not part of this spec.

These decisions were made before this spec was written. Everything below follows them.

- **Performance only.** The system measures performance in a quarter. Its score is meant to support increment decisions. It does not decide promotion or level change. That is a separate judgment and will be designed later.
- **Developers only, levels L1 to L3.** The pilot scores Software Engineers at level 1, 2 and 3. SWE L4 is a people-leader level. The Engineering Manager keeps evaluating L4 the way it is done today.
- **The official framework is the reference.** A developer is scored against the DSi Career Framework for Software Engineers, published at careerpath.innovatorslab.net. The system uses the framework's names and level statements exactly. It does not write its own.
- **No daily entry by developers.** Developers do not log their activity. The system reads the records that normal work already creates.
- **The AI scores, the lead corrects.** The AI judges every piece of work, and the score comes from evidence. People are involved in three places only. A Team Lead can correct the AI, with a written reason. A Team Lead scores the things no tool can see. A developer can add work that no system recorded, and the lead confirms it. Developers cannot dispute or change anything else.
- **Everything is tracked in OpenProject.** Every task, bug, production error and support request must exist as an OpenProject item. There is no separate bug tracker or helpdesk to read from.

## 2. The idea in short

Think about how an experienced engineer judges a colleague. They do not read a long description and tick boxes. They look at the work and ask one question: "Is this the kind of work we expect from an L3?"

The system does the same thing in three moves.

1. Every piece of work gets a **tier** when it is recorded. The tier says which level's kind of work it is. T1 is L1-kind work, T2 is L2-kind work and T3 is L3-kind work.
2. At the end of the quarter, for each dimension of the career framework, the system finds the tier where the person's work actually sits.
3. It compares that tier with the person's level and gives the dimension a score. Same as the level scores **1**. Higher scores **2**. Lower scores **0**.

A short example. Rafi is an L2. Most of his feature work this quarter was Medium complexity, which is T2. T2 matches L2, so Rafi scores 1 on the delivery dimension.

The dimension scores are then combined into one weighted score for the quarter. That score is the only overall result. The system never gives a person an overall label such as "meets expectations".

### Four rules that never change

These four rules apply in every step. If a future change breaks one of them, the change is wrong.

1. **Higher levels do different work, not more work.** Nobody is compared on volume. Ten easy tasks do not equal one hard one.
2. **Analytics describe the work. Tiers judge it.** Every number is shown on the dashboard. Only the tiers of items and five quality checks enter the score.
3. **A bug is never blamed on the last person who touched the code.** A bug counts against a person only when there is a recorded cause that points to their work.
4. **Not being asked never counts against anyone.** If the project gave a person no chance to do something, that is a staffing problem for the project. It is not a low score for the person.

## 3. Words used in this spec

These names are fixed. The spec, the database and the screens all use the same names, and no other name is used for the same thing.

### Main names

| Name | Meaning | Example |
| --- | --- | --- |
| Data source | A system that records are pulled from | Git host, OpenProject, CI/CD |
| Evidence item | One record from a data source, linked to one person | One person's part of a closed feature, one review comment |
| Evidence type | The kind of an evidence item. There are 18 kinds | Feature work, Review comment, Test change |
| Tier | T1, T2 or T3 on each item. It says which level's kind of work the item is. T4 exists for later use | High complexity feature work is T3 |
| Analytic | A number or chart calculated from items, shown on the dashboard | Share of work by type, work complexity |
| Quality gate | One of five analytics that also affect the dimension score, as pass or fail | Tests in at least 90% of feature PRs |
| Dimension | One row of the official DSi Career Framework, with one statement per level | Coding and code quality |
| Dimension score | The result for one dimension in one quarter. It is 0, 1 or 2. A dimension that cannot be judged fairly gets no score and is marked "Not assessed" | 1 |
| Score | One number per person per quarter. It is the weighted average of the dimension scores | 1.0 means a dimension score of 1 on every scored dimension |

### Two helper words used in the scoring rule

| Name | Meaning |
| --- | --- |
| Presence | A person has presence at a tier when enough of their items sit at that tier or higher. Section 9 gives the exact rule |
| Working tier | The highest tier at which the person has presence. The profile shows it as "works at T2" |

### People

| Name | Meaning |
| --- | --- |
| Developer | A Software Engineer (SWE) at level L1, L2 or L3. This is the person being scored |
| Team Lead | The lead of the developer's project team |
| EM | Engineering Manager. The EM scores a Team Lead's own lead-rated dimensions, reviews the corrections that leads make, and can sort developers of the same level by score |

### Short forms

| Short form | Meaning |
| --- | --- |
| PR | Pull request. A set of code changes sent for review in Git |
| CI/CD | The automatic pipeline that builds, tests and deploys code |
| UAT | User acceptance testing. The client tests the software before accepting it |
| QA | Quality assurance. The internal testing stage before UAT |
| ADR | Architecture decision record. A short document that records a design decision and why it was made |
| KT | Knowledge transfer. Handing knowledge to another person or team |
| DSI Central | DSi's internal system for project assignments and allocation |
| Hygiene | How well a project keeps its records. For example, whether every bug is linked to its feature |

## 4. The whole flow at a glance

The system works in six steps. Sections 5 to 10 describe them one by one, in this order.

&#91;embedded content: process flow · six steps from data source to profile\]

Read the picture from top to bottom. Records are pulled from the data sources and become evidence items. From there the path splits in two. On the left, every item feeds the analytics on the dashboard, which show the work and score nothing. On the right, every item's tier is read by a dimension, and that produces the dimension score. Only five analytics cross from the left side to the right side, as quality gates.

## 5. Step 1 — Collect records from the data sources

The system reads from eleven sources. Nine of them record work a person did. Two of them only give context and never count as a contribution. This step runs every day.

### Sources that record work

| # | Source | What is pulled | How it is linked to a person and a work item |
| --- | --- | --- | --- |
| 1 | Git host (GitHub or GitLab) | Commits, PRs, reviews and review comments, files changed, release tags, pipeline files, READMEs | Person: Git handle or commit email, looked up in the identity map. Work item: the OpenProject id in the branch name or commit message |
| 2 | OpenProject work items | Features, tasks, Design tasks, bugs, status history, links, reopens, sprint moves with their reason | Person: assignee and creator. Work item: the item itself |
| 3 | CI/CD, where a project has it | Production deployments with their commits, and rollbacks | Commit to PR to task to feature |
| 4 | OpenProject meetings | Outcomes, decisions and action items from design reviews and client meetings | Named attendee and linked work item |
| 5 | Client delivery records kept by the PM or BA | Demos, UAT, handover and client training, with the presenter named | Named person and project |
| 6 | Pre-sales repository | Estimates, technical approach sections, proofs of concept, bid outcome | Author and bid id |
| 7 | HR and learning records | Interviews taken, trainings given, certifications, and formal mentoring assignments | Organization email |
| 8 | Lead highlights | Work the Team Lead marks as notable, with a short reason and a linked item | Linked item |
| 9 | Self-added work | Work the person adds because no system recorded it. It counts only after the lead confirms it | Confirmed by the lead |

### Sources that give context only

| # | Source | What is pulled | What it is used for |
| --- | --- | --- | --- |
| 10 | DSI Central | Project assignments with their dates, and allocation % | Knowing where the person was expected to work. It feeds the opportunity profile and the confidence check, both explained later |
| 11 | Module ownership, worked out by this system from Git | The owner of each module. Nobody enters this by hand. The system proposes the person with the largest share of meaningful changes and reviews on the module over the last six months. The lead corrects the owner only when the proposal is wrong, with a written reason | Knowing which L3 owns which module |

### What happens to each record

Every record that is pulled becomes one **evidence item**. Section 6 describes the item in full. Three rules apply from the moment a record is stored.

- **Items are never edited or deleted.** A correction is a new item that replaces the old one. This way any score can be traced back and reproduced later.
- **One identity per person.** Every employee has one organization email, and that email is the person's identity in this system. OpenProject, DSI Central and the other internal systems already use it, so they need no mapping. The system keeps one small map: organization email to the person's outside identities, which are the GitHub or GitLab handle and any other email used in Git commits. An item with an unknown handle is stored but belongs to nobody until the handle is mapped. Unmapped handles are listed every week.
- **Every item keeps a link back to its source record,** so a person can always open the original.

### Rules about the data

- **Bugs come only from OpenProject.** QA, the PM or support logs each bug, links it to its feature, and sets a "found in" stage. The stage is one of review, QA, UAT or production.
- **Production bugs count for 30 days.** A production bug counts against feature work only if it is found within 30 days of the feature's release.
- **Release date.** It comes from CI/CD where a project has it. Otherwise the PM or lead marks the feature "Released" in OpenProject. The system records which method was used.
- **Sprint moves carry a reason.** When a task moves to a later sprint, the reason is one of client, dependency, blocked, scope or own. Only "own" counts against the person.
- **Design work is tracked as a Design task.** A feature that needs real design gets a child Design task in OpenProject, with the design attached and approved by the lead.

### What the system does not use

Build pass or fail counts, lines of code, time tracking, chat, standup notes, private messages, the OpenProject wiki and code quality scanners are not used.

## 6. Step 2 — Turn each record into an evidence item with a type and a tier

This is the most important step. All the judgment in the system happens here, when an item gets its tier. Everything after this step is counting.

### 6.1 What every evidence item carries

Every item has the same common fields.

| Field | Meaning |
| --- | --- |
| Person | Who did the work |
| Project | Which project it belongs to |
| Date | When it happened |
| Source link | A link back to the original record |
| Related items | Links to connected items. A PR links to its task. A task links to its feature. A bug links to a task or a feature |
| Evidence type | One of the 18 kinds listed in 6.4 |
| Tier | T1, T2 or T3. Section 6.3 explains how it is set |
| Marker | Optional. "Lead highlight" or "self-added" when the item came from source 8 or 9 |

### 6.2 Feature work, complexity and work type

Before tiers can be set, the system does three things. It groups tasks into feature work, it sets the complexity of each group, and it gives every item a work type.

**Feature work.** The unit of delivery is one person's part of one feature. The system groups all tasks under a feature that are assigned to the same developer, together with their PRs. This group is one evidence item of the type "Feature work". A task with no parent feature is a feature work item by itself.

There are no roles such as primary or supporting. Each developer is judged on their own part. It does not matter whether that part was split into one task or ten tasks. A reviewer is credited separately, through Review comment items.

Example. A payment feature has eight tasks. Rafi did six of them, which included a new gateway integration and a data migration. Nadia did two, a settings screen and some labels. Rafi's feature work can be High and Nadia's can be Low, although both worked on the same feature.

**Complexity.** Each feature work item is Low, Medium, High or Very high. The AI sets the value once, when the feature closes. It reads the person's tasks and all their linked PRs together. From the tickets it takes the titles, descriptions, linked items and priority. From the PRs it takes the modules touched, schema changes or data migrations, new integrations and the nature of the code change. The AI's value stands unless the Team Lead changes it. A change needs a written reason. The item counts in the quarter in which the feature closes.

| Complexity | When it applies | Tier |
| --- | --- | --- |
| Low | One module, no data change, a known pattern | T1 |
| Medium | One or two modules, or a schema change, or one integration | T2 |
| High | Three or more modules, or a new integration, or unclear requirements, or a production data migration | T3 |
| Very high | Work across systems, or new architecture, or client-critical work with no earlier example | T4, for later use |

Two limits apply when the AI reads the code.

- **Size is not complexity.** The AI must not treat a large change as hard work. A 2,000-line generated file is Low. A 30-line fix to a hard concurrency bug can be High. Lines of code are never used as a measure.
- **Client-owned repositories.** On some client projects the system may not be allowed to read the code, and only metadata is available, such as which files changed. There the AI uses the ticket and that metadata. The item is marked "judged without code", so the lead knows the AI had less to work with.

**Work type.** The AI gives each PR and work item exactly one of 13 work types: feature, enhancement, fix, maintenance, research, technical task, operational support, refactor, test, docs, infra, performance, security. One type per item means each item is counted in exactly one place. Only the Team Lead can change a work type, with a written reason.

### 6.3 What a tier means and who sets it

A tier says which level's statement the work matches. The framework has one statement per level for each dimension. T1 is work of the kind the L1 statements describe. T2 matches the L2 statements. T3 matches the L3 statements. T4 matches the L4 statements and is kept for later.

Because an item's tier and a person's level point to the same statements, the two can be compared directly. This is what makes the scoring in Step 5 simple.

The framework's statements grow in the same way from level to level. L1 is about the person's own task. L2 is about their own feature. L3 is about a module or a team. L4 is about work across projects. When the AI or the lead meets a case that the table in 6.4 does not cover, they use this pattern to choose the tier.

| Who | What they do | When |
| --- | --- | --- |
| System, using AI | Sets the tier of every item from its content and links. It uses the complexity of feature work, the type of a review comment, the work type, which files were changed and who the audience of a session was | When the item is created. For feature work, when the feature closes |
| Team Lead | Can change a tier when the AI got it wrong. A written reason is required. The AI's original tier is kept next to the new one | Any time until the quarter is closed and published |
| Developer | Cannot change or dispute a tier. A developer who thinks a tier is wrong tells their lead, and the lead decides | Not applicable |

The lead is not asked to confirm each tier. The AI's tier stands unless the lead changes it. Section 9.8 explains how the EM can see every change a lead makes.

### 6.4 The tier table

This is the central table of the system. For each evidence type it says what T1, T2 and T3 work looks like. It is written once and is the same for every person. "Not used" means the evidence type has no work at that tier.

| Evidence type | T1 | T2 | T3 | T4, later |
| --- | --- | --- | --- | --- |
| Feature work | Low complexity | Medium complexity | High complexity | Very high complexity |
| Review comment written | A question or a small catch | Logic, edge case or readability | Design, security or performance | Architecture or cross-system |
| Test change | Unit test in own PR | Integration or regression test | A test gate in CI for a module | Performance, load or security test |
| Bug fix | Bug in own area | High-severity or production bug with the root cause recorded | Critical or cross-module bug with a safeguard added, such as a test or an alert | Not used |
| Design artifact | Not used | Design note on own feature | Design task with options compared, or an ADR | Design decision across projects |
| Refactor and performance item | Cleanup in own PR | Small refactor or speed-up on own initiative | Planned debt item, or a dependency kept current, or a measured bottleneck removed | Not used |
| Security item | Fix to a reported vulnerability in own code | Dependency fix or access-control fix | Security check added to the team pipeline | Not used |
| Infra and release item | Not used | Pipeline fix, Dockerfile, logging, or cutting a release (tag, branch, hotfix) | Team pipeline or monitoring | Not used |
| Planning item | Estimate given and status kept current on own tasks | Own feature broken into tasks, and the estimate held | Feature or module broken into tasks that others pick up | Not used |
| Documentation | README or setup notes for own work | Feature or API docs for own work | Team runbook, setup guide or integration guide kept current | Reusable standard or playbook |
| Mentoring item | Not used | A formal assignment to mentor an intern, new joiner or L1 for a period | A formal assignment to mentor a mid-level engineer, or several mentees in the same quarter | Mentors an L3 |
| Knowledge session | Not used | Session inside the team | Session beyond the team | Company-level session |
| Onboarding and KT item | Helps a new member settle in, recorded on an onboarding task | Onboards a new member, or writes handover notes when someone leaves | Owns the onboarding or handover plan for the project | Not used |
| Collaboration item | Responds in a review thread or design thread | Comments on another person's Design task, or closes a review or item in another team's project | Drives a decision in a design review, or closes a cross-team item, or raises a dependency on another team's item before it blocks work | Not used |
| Client-facing item | UAT issue on own feature work resolved | Demo or handover notes for own feature | UAT or KT session for a module, or a requirement workshop with an outcome they own | Acceptance plan across projects |
| Recruitment item, only when HR invited the person | Supports hiring when asked | Candidate assessed | Advanced interview, or assessment questions written | Not used |
| Pre-sales item | Demo data or information collected | Estimate for familiar work, or demo set-up | Technical solution, estimate or delivery plan for a bid | Owns the proposal |
| Component ownership | Not used | Not used | A module the person has owned for 6 months or more | Not used |

Component ownership is special. It is not read by any dimension. It feeds quality gate G5 and is shown on the profile as context.

### 6.5 The fields each evidence type carries

This table is the data model. On top of the common fields in 6.1, each evidence type stores the fields below. The last column says which dashboard analytics are calculated from it. Details found during implementation should be added to this table.

| Evidence type | Fields | Analytics it feeds |
| --- | --- | --- |
| Feature work | Complexity, the tasks in the group, work type, dates (started, done, released), estimate, sprint moves with reason, linked bugs and reopens with severity and found-in stage, modules changed, linked PRs, whether the AI could read the code or only its metadata | Work classification, Completed work, Work complexity, Delivery outcome, Issues on own work, Planning |
| Review comment written | PR, author reviewed, comment type, substantive or style, date | Review participation, Review effectiveness. The same records, read from the PR author's side, feed Review findings received |
| Test change | PR, linked task or fix, test kind (unit, integration, regression, CI gate), coverage of changed lines where CI reports it | Testing contribution |
| Bug fix | Bug item, severity, found-in stage, time to resolve, root cause recorded or not, linked safeguard, modules touched | Fixes and root cause. Also Issues on own work, from the feature work side |
| Design artifact | Design task or ADR, linked feature or module, whether the "options considered" field is filled, lead approval, date | Architecture and design, Design collaboration |
| Refactor and performance item | PR, work type, modules touched, dependency changes, measured before and after result where recorded | Tech debt and refactoring, Performance improvements |
| Security item | PR or item, kind (vulnerability fix, dependency upgrade, access control), vulnerability reference where known | Security work |
| Infra and release item | PR or tag, kind (pipeline, container, infrastructure code, monitoring, release tag, release branch, hotfix), scope (own feature or team) | Infra and release |
| Planning item | Tasks created under a feature, estimates given, estimate against actual on own tasks, whether status was kept current | Planning |
| Documentation | Files changed (README, docs folder, runbook, API doc), linked feature, scope (own work or team) | Documentation |
| Mentoring item | Mentor, mentee and their level, period. Taken from the HR and learning records | Mentoring |
| Knowledge session | Topic, audience (team, beyond team, company), date, source | Knowledge-sharing sessions |
| Onboarding and KT item | New joiner or leaver, kind (settle-in help, onboarding run, handover notes, onboarding plan), linked onboarding task | Knowledge transfer |
| Collaboration item | Meeting or item, kind, other team involved, outcome closed or not | Design collaboration, Cross-team support |
| Client-facing item | Kind (UAT issue resolved, demo, handover notes, UAT or KT session, requirement workshop), feature or module, outcome owned, client meeting link | Client demos, Technical workshops, UAT contribution, Training and handover |
| Recruitment item | HR invitation, kind (support, candidate assessment, advanced interview, questions written), date | Recruitment |
| Pre-sales item | Bid id, kind (information, demo set-up, estimate, technical solution, delivery plan), bid outcome as context only | Pre-sales |
| Component ownership | Module, owner from and to dates, whether the lead corrected the owner, share of meaningful changes and reviews over six months, other active contributors | Component ownership share, Knowledge spread |

Three analytics do not come from a single evidence type.

- **Skills** is read from the files changed across all of a person's PRs. It lists the languages, frameworks and tools used, and marks which are new for the person. Guessing from file names whether someone learned something new is not reliable enough to rate. So Skills is not an evidence type. It is only shown to the lead as context.
- **Learning records** are trainings and certifications. They come straight from HR as context.
- **Validated additional work** is any item marked "lead highlight" or "self-added", whatever its type.

### 6.6 Assignments and the opportunity profile

**Assignments.** The system reads from DSI Central which projects each person is assigned to, from when to when, and at what allocation %. It uses this for two things only. It tells the system which project's work to compare the person with. It also feeds the confidence check in Step 5.

**Opportunity profile.** It records what the project offered and what the person took.

| Part | Meaning |
| --- | --- |
| Offered | Feature work items closed on the person's projects during their allocated time, counted by complexity |
| Taken | The feature work items among those that are the person's own |
| Client exposure | Whether the project had any demo, UAT or client meeting |
| Juniors present | Whether an L1, intern or new joiner was on the team |
| Releases | Whether there was any production release |
| Invitations | Whether HR invited the person to hiring, or the person was invited to a bid |

Step 5 reads the opportunity profile before it scores anything. This is how rule 4, "not being asked never counts against anyone", is enforced.

## 7. Step 3 — Show the analytics on the dashboard and check the five quality gates

An analytic is a number or a chart that the system calculates from a person's evidence items. Analytics exist so that a developer can see an honest picture of everything they did. They are shown on the developer's dashboard.

Analytics are **not** scored. A high number does not raise a dimension score and a low number does not lower it. The only exception is the five quality gates in 7.3.

Every count on the dashboard is split by tier, so a busy number also shows at which level the work was.

### 7.1 One rule for all bug numbers

A bug is linked to a person only in one of three ways.

- The bug is linked to one of the person's own tasks, or to a feature that only this person worked on.
- The bug has a recorded "caused by" link to the person's work.
- The lead confirmed the cause.

Nothing is linked through file history. This enforces rule 3.

### 7.2 The analytics, by group

Each table says what the analytic means and what the dashboard shows for it. Section 6.5 lists which evidence types each analytic is calculated from, so that is not repeated here.

**Work delivery and outcomes**

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Work classification | What kind of work the person spent the quarter on | A pie chart of closed items by work type. For example 60% feature, 20% fix, 10% refactor, 10% docs |
| Completed work | How much the person finished | Simple counts: feature work items closed, tasks closed, and bugs closed by severity |
| Work complexity | How hard the person's work was | A bar chart of the person's feature work by Low, Medium and High. Next to it are two comparison bars: the middle value for the same level across DSi, and what the project offered. This is the picture for one quarter. The "Work level over time" chart in section 11 shows the trend |
| Delivery outcome | Whether the person finished what they committed to in each sprint | Sprint tasks done divided by tasks committed, per sprint and as an average for the quarter. A task moved out for a reason other than "own" is left out of the count |

**Quality and reliability**

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Review findings received | How many real problems reviewers find in the person's code before it is merged | Substantive review comments received per PR, by type. Style comments are left out |
| Issues on own work | How many problems appear after the person hands their work over | Valid bugs found in QA, UAT or production, tasks reopened after Done, and rollbacks, on the person's own feature work. The numbers are split by stage. Each problem is counted once. Duplicates, "not a bug" items and reopens caused by a change request are left out |
| Testing contribution | Whether the person writes tests together with their code | The share of feature and fix PRs that include test changes, or coverage of changed lines where CI reports it. Also the share of bug fixes that add a regression test |
| Fixes and root cause | How the person handles serious bugs, whoever caused them | Production bugs and High or Critical bugs the person resolved, the time to resolve them, the share with a root cause recorded, and the fixes that added a safeguard |
| Security work | The person's part in keeping the software secure | Security items the person fixed, and dependency upgrades that close a known vulnerability |

**Review and collaboration**

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Review participation | How much the person reviews other people's code | The number of PRs reviewed and the number of different authors reviewed. It is always shown next to Review effectiveness |
| Review effectiveness | Whether the person's reviews find real problems | Substantive comments per PR reviewed, and the mix of comment types (correctness, security, performance, design, maintainability). An approval with no substantive comment counts as zero |
| Design collaboration | Whether the person takes part in design discussions | Design reviews where the person is a named attendee with a recorded contribution, and comments on other people's Design tasks |

**Ownership and improvements**

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Component ownership share | Which modules the person carries | Per module, the person's share of meaningful changes and reviews over the last six months. The person with the largest share is proposed as the owner, and the lead can correct it |
| Knowledge spread | Whether knowledge of an owned module is shared, or sits with one person | For each module the person owns, the number of other active contributors in the quarter |
| Architecture and design | The design work the person produced | Design tasks the person wrote and the lead approved, ADR files, and whether options were compared |
| Tech debt and refactoring | Work that keeps the code healthy | PRs with work type refactor, and dependency upgrades and removals |
| Performance improvements | Work that makes the software faster or more stable | PRs with work type performance. A measured before and after result is shown when one is recorded |
| Infra and release | The person's part in pipelines, infrastructure and releases | Changes to pipeline files, Dockerfiles, infrastructure code and monitoring config. Also release tags, release branches and hotfixes cut by the person |
| Planning | How well the person breaks down and estimates work | Tasks the person created under features, estimates given, and estimate against actual on own tasks |
| Documentation | What the person wrote down for others | Documentation changes by the person: README, docs folder, runbooks, API docs |
| Skills | Which technologies the person worked with | Languages, frameworks and tools in the person's changes this quarter, split into previously used and new |

**Knowledge and organization**

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Mentoring | Help the person gave to juniors | The person's formal mentoring assignments: who they mentored and for how long |
| Knowledge-sharing sessions | Sessions the person gave to teach others | Sessions the person delivered, with topic and audience |
| Knowledge transfer | Help with onboarding and handover inside DSi | KT and onboarding sessions the person ran, handover notes, and help to new joiners recorded on onboarding tasks |
| Cross-team support | Help the person gave outside their own project | Work items or reviews in projects the person is not allocated to, with the outcome |
| Recruitment | Help with hiring | Interviews taken, candidates assessed and assessment questions written, only when HR invited the person |
| Learning records | Formal learning the person completed | Trainings completed and certifications earned |
| Validated additional work | Work that no system captured | Self-added work confirmed by the lead, and lead highlights |

**Client and business**

| Analytic | What it means | What the dashboard shows |
| --- | --- | --- |
| Client demos | Presenting work to the client | Demos where the person is the named presenter |
| Technical workshops | Working with the client on requirements and solutions | Client meetings where the person is named with an outcome they own |
| UAT contribution | Support during the client's testing | UAT sessions the person supported and UAT issues they resolved, by severity |
| Training and handover | Handing the software over to the client | Client training and KT meetings where the person presented, and handover items completed |
| Pre-sales | Help with winning new work | Proposal sections written, estimates given and proofs of concept built. A lost bid never counts against anyone |

**Charts that show the level of the work.** The dashboard also has the "Work level over time" chart and the "works at" tier per dimension. Section 11 describes them.

### 7.3 The five quality gates

A tier can show how hard the work was. It cannot show whether the work was done with good habits. The quality gates fill this gap. Five analytics are checked as pass or fail, and the result affects the dimension score in Step 5.

Three things are true for every gate.

- **A gate never raises a dimension score.** It can only hold a 2 down to 1, or pull the score down to 0.
- **A gate is the same at every level.**
- **A gate never punishes a person for a project decision.** G1, G2 and G4 compare the person with their own project in the same quarter. A person fails only when they are clearly worse than their own teammates.

| Gate | Pass | Close miss | Far miss |
| --- | --- | --- | --- |
| G1 Tests with features | The person's share of feature PRs that include test changes is not lower than the same project's share in the same quarter by more than 10 points | Lower by 10 to 25 points | Lower by more than 25 points |
| G2 Regression tests | The person's share of bug fixes that add a regression test is not lower than the same project's share in the same quarter by more than 10 points | Lower by 10 to 25 points | Lower by more than 25 points |
| G3 Sprint commitment | At least 85% of committed tasks done. Only misses with reason "own" count | 70% to 84% | Under 70% |
| G4 Escaped defects | The share of the person's feature work items with a valid production bug is not worse than the same project's share in the same quarter by more than 10 points | Worse by 10 to 25 points | Worse by more than 25 points, or any Critical production bug with a recorded cause on the person's own feature work |
| G5 Knowledge spread, L3 only | Each module the person owns has at least 2 other active contributors in the quarter | No close miss | A module where the owner is the only contributor |

**Why G1 and G2 compare inside the project.** A team under heavy deadline pressure may decide to skip tests. That is a decision of the project, not of one developer. It would be unfair to score that developer lower than someone on a team with plenty of time. So the gate asks only one question: is this person clearly worse than their own teammates?

Skipped tests still stay visible in two places.

- **On the dashboard.** The Testing contribution analytic shows the person's own numbers as they are, whatever the team did.
- **In a report to management.** DSi's standard is tests in at least 90% of feature PRs and a regression test in at least 90% of bug fixes. When a whole project falls below this standard, the project is flagged to the EM and the CTO. Section 10.3 describes the flag.

When a project has fewer than three developers, there is nobody to compare with. In that case G1 and G2 have no effect and the trail notes "gate unverified".

**Why G4 compares inside the project.** Bug rates depend on the project's QA, its client and its modules. A company-wide target would not be fair, and the person's own past would not be fair either. When a project has fewer than three developers or fewer than 15 feature work items, the comparison is skipped. In that case G4 fails only when a Critical production bug with a recorded cause exists on the person's own feature work.

All numbers in this table are starting values. Section 14 lists them as settings.

## 8. Step 4 — Connect evidence types to the dimensions of the career framework

The official DSi Career Framework for Software Engineers has 6 growth areas and 22 dimensions. Each dimension has one statement per level, written in the first person. For example, the L1 statement for Coding and code quality begins "I write clear code that follows my team's patterns".

The system does not copy or change these statements. They live at careerpath.innovatorslab.net. For each dimension the system stores only four things.

1. **A weight per level.** It says how much the dimension counts in the score at that level.
2. **The evidence types that feed it.** The tiers of these items are read in Step 5.
3. **The quality gates that apply to it.**
4. **When it is possible.** The condition under which the person had a chance to show this dimension.

### 8.1 Weights

Each dimension has a weight for each level. The weight says how much the dimension counts in the score at that level.

- Weights are numbers kept in settings. They can be changed at any time without changing the system.
- A weight of 0 means the dimension is not counted at that level.
- This spec proposes a weight for each dimension at each level. It is only a proposal. Management confirms the weights or changes them.

The official framework does not rank its dimensions. So the weights are a proposal of this system and are not part of the framework.

### 8.2 Two kinds of dimension

Some dimensions leave evidence in tools. Coding is one. The system scores these from evidence items, and they are called **evidence-backed**.

Other dimensions are about knowledge or behavior that no tool records. Feedback and self-awareness is one. The Team Lead scores these, and they are called **lead-rated-only**.

Of the 22 official dimensions, 9 are evidence-backed and 13 are lead-rated-only. The system also proposes one new evidence-backed dimension, Client delivery, which is explained in 8.4.

### 8.3 Evidence-backed dimensions

How to read the table:

- **Proposed weight** gives the weight for an L1, L2 and L3 developer, in that order.
- **Evidence types** lists every evidence type that this dimension reads.
- **Details** explains any special rule for the row.

| Growth area | Dimension | Proposed weight | Evidence types | Quality gates | Possible when | Details |
| --- | --- | --- | --- | --- | --- | --- |
| Engineering Skills | Coding and code quality | 3 / 3 / 3 | Feature work, Review comment written | None | Always | Feature work is read after the clean-delivery rule in 9.2. Review comments are read only for L2 and L3 developers, because the L1 statement does not ask for code reviews |
| Engineering Skills | Design and architecture | 1 / 2 / 3 | Design artifact | None | A feature the person worked on had a Design task | Only a design approved by the lead counts. A design written only in a PR description can be added as self-added work. An L1 developer is Not assessed here unless they wrote a design note, because the L1 statement is about understanding design |
| Engineering Skills | Engineering standards and system health | 2 / 3 / 3 | Test change, Bug fix, Refactor and performance item, Security item, Infra and release item | G1, G2, G4 | Always | An L3 developer needs two T3 items here, not one |
| Leadership | Planning and execution | 3 / 3 / 3 | Feature work, Planning item | G3, G5 | Always | Feature work is read with its plain tier, without the clean-delivery rule. Gate G5 applies only to L3 developers |
| Leadership | Collaboration and negotiation | 2 / 2 / 3 | Collaboration item | None | The person's project worked with another team, or held a design review, in the quarter | Collaboration items are read only for L2 and L3 developers. For an L1 developer the lead rates this dimension, because the L1 statement describes behavior only |
| Leadership | Mentoring and people leadership | 1 / 2 / 3 | Mentoring item | None | The person was given a formal mentoring assignment | For an L3 developer the lead also answers one question: does the person lead their small team fairly? If the answer is no, the dimension scores 0 |
| Organizational Contribution | Knowledge sharing | 1 / 2 / 2 | Documentation, Knowledge session | None | Always | Documentation edits of only a few lines are ignored |
| Organizational Contribution | Hiring and onboarding | 1 / 1 / 2 | Onboarding and KT item, Recruitment item | None | A new joiner or leaver was on the project, or HR invited the person | Recruitment items exist only when HR invited the person |
| Organizational Contribution | Pre-sales and business growth | 1 / 1 / 2 | Pre-sales item | None | The person was invited to a bid | If DSi has no pre-sales repository, only lead-highlighted work counts. A lost bid never counts against anyone |
| Proposed, not official | Client delivery | 1 / 2 / 2 | Client-facing item | None | The project had a demo, UAT, handover or workshop | The record names the presenter, or the lead confirms who presented |

**Mentoring needs a formal record.** Mentoring is not the same as writing review comments, so review comments are never counted as mentoring. DSi does not record mentoring today. The plan is to make it formal: a mentoring assignment is created in the HR and learning records, with the mentor, the mentee and the period. Until that record exists, this dimension is Not assessed for everyone, and nobody loses points for it.

Feedback from the mentee, collected with a form, is future scope. It would let the system judge how good the mentoring was, and not only that it happened.

### 8.4 The proposed Client delivery dimension

Developers at DSi do real client work: UAT, demos, handover and requirement workshops. The official framework has no dimension for it. The system keeps this work and does not drop it. It is scored as a separate, proposed dimension so that the Career Framework team can decide whether to add it to the official framework.

### 8.5 Lead-rated-only dimensions

Once a quarter the Team Lead reads the statement at the person's level and gives a score of 0, 1 or 2. A score of 1 means the statement is met. For a 0 or a 2, the lead must point to one evidence item as the reason. The "Context shown" column says what the system shows next to the row to help the lead.

| Growth area | Dimension | Proposed weight | Context shown to the lead |
| --- | --- | --- | --- |
| Engineering Skills | Technical strategy and innovation | 1 / 1 / 2 | Nothing |
| Growth / Learning | Learning and skill depth | 3 / 2 / 2 | Skills analytic |
| Growth / Learning | Staying current | 1 / 1 / 2 | Skills analytic |
| Growth / Learning | Feedback and self-awareness | 3 / 2 / 2 | Nothing |
| Growth / Learning | Owning my growth | 1 / 1 / 2 | Learning records |
| Leadership | Communication | 2 / 2 / 3 | Client-facing items, such as demos presented. The lead also judges here whether the person raises risks and blockers early |
| Leadership | Decision making | 2 / 2 / 3 | Design artifacts, to see the options compared |
| Organizational Contribution | Representation and branding | 1 / 1 / 1 | Nothing |
| Organizational Contribution | Improvement and company initiatives | 2 / 1 / 2 | Project hygiene %, at L3 |
| Values Alignment | Practicing and promoting DSi's core values | 3 / 3 / 3 | Nothing for now |
| Business and Strategic Alignment | Strategic Direction | 1 / 1 / 2 | Nothing |
| Business and Strategic Alignment | Business Contribution | 2 / 2 / 3 | Estimate against actual, and sprint moves with reason client or scope |
| Business and Strategic Alignment | Continuous Innovation | 1 / 1 / 2 | Nothing |

Raising risks early is judged by the lead and not read from OpenProject. Teams use the tracker's risk flags too differently for it to be a fair measure of a person.

### 8.6 Mapping rules

- **One home per evidence type.** The tiers of an evidence type are read in one dimension only. Feature work is the one exception. It is read in two dimensions: in Planning and execution for what was delivered, and in Coding and code quality for the complexity of the code written. The framework's own statements overlap in the same way.
- **Mentoring comes from formal assignments.** A Mentoring item exists only when a mentoring assignment is recorded in the HR and learning records. Every review comment is read in Coding and code quality.
- **Component ownership has no home dimension.** It feeds gate G5 and is shown as context.
- **A gate sits only where worse work could make the evidence look better.** More PRs without tests look like more output, so G1 guards against that. G4 sits on Engineering standards only, so one production bug lowers one dimension and not three.
- **Each level includes the levels below it.** An L3 whose work sits at T3 but who fails G1 is still held at 1 or 0 on Engineering standards.
- **The mapping has a version per quarter.** A dimension score can always be explained with the mapping that was in force when it was made.

## 9. Step 5 — Work out the dimension scores at the end of the quarter

Steps 1 to 3 run every day. Step 5 runs once, when the quarter closes. At that moment the evidence items of the quarter are frozen, and the same procedure runs for every person and every dimension.

The procedure gives each dimension a **dimension score** of 0, 1 or 2. A dimension that cannot be judged fairly gets no score and is marked **Not assessed**.

Two things are important to understand before reading the procedure.

- **Each quarter is scored on its own.** A dimension score does not depend on earlier quarters.
- **A dimension score is about one dimension only.** The system never gives an overall verdict on a person, and it does not decide level change.

### 9.1 The procedure for an evidence-backed dimension

The system runs these seven steps in order. A developer should be able to follow them by hand for their own profile.

**1. Collect the items.** Take every item of the dimension's evidence types that belongs to the person in this quarter.

**2. Check whether it was possible.** If there are no items, look at the opportunity profile and the "Possible when" condition of the dimension. If the person had no chance, the dimension is **Not assessed** and the procedure stops. If the person had a chance, continue.

**3. Apply the clean-delivery rule.** This step runs only for Coding and code quality. Section 9.2 explains it.

**4. Find the working tier.** Section 9.3 explains how.

**5. Compare the working tier with the level.**

| Working tier compared with the person's level | Dimension score |
| --- | --- |
| Lower, or no working tier at all although it was possible | 0 |
| Same number, for example T2 for an L2 | 1 |
| Higher, for example T3 for an L2 | 2 |

There is one exception. Sometimes the project offered no work at the tier the level expects. For example, an L3 is on a project that closed no High feature work all quarter. In that case the person is scored against the hardest work that was available. Taking that work scores 1. Declining it scores 0. The project also gets a staffing flag such as "L3 capacity unused".

**6. Apply the quality gates of the dimension.**

| Gate result | Effect on the dimension score | Note stored on the trail |
| --- | --- | --- |
| All gates pass | The score stays | None |
| Any gate is a close miss | A 2 becomes 1. A 1 or a 0 stays | "held: G1" |
| Any gate is a far miss | The score becomes 0 | "gate: G4 far" |
| A gate has no data | No effect | "gate unverified" |

**7. Check confidence.** Section 9.4 explains it. If confidence is low and the score is 0, the dimension becomes **Not assessed (insufficient evidence)**.

The system stores the dimension score together with the items, their tiers, the gate results and every note. This stored record is called the **trail**.

### 9.2 The clean-delivery rule

The statements for Coding and code quality ask for quality as well as complexity. A tier alone cannot see quality. So in this one dimension, a feature work item keeps its tier only if it was delivered clean.

A feature work item is **not clean** when one of these is linked to it.

- A valid bug of High or Critical severity found after handover, in QA, UAT or production.
- A reopen of one of its tasks after the task was marked Done.

| Feature work | Delivered clean | Not clean |
| --- | --- | --- |
| High | Counts as T3, weight 4 | Counts as T2, weight 2 |
| Medium | Counts as T2, weight 2 | Counts as T1, weight 1 |
| Low | Counts as T1, weight 1 | Keeps weight 1 but sits at no tier. It lowers the share at T1, so an L1 can score 0 |

Low-severity bugs do not lower the tier. This way a project with strict QA does not punish its developers.

The rule applies only in Coding and code quality. In Planning and execution the same item keeps its plain tier, because that dimension is about delivery and gate G3 already guards it.

### 9.3 How the working tier is found

Evidence types fall into two groups.

- **Frequent types:** Feature work, Review comment written, Test change. A person has many of these in a quarter.
- **Rare types:** all the others. A person has a few of these in a quarter, or none.

A person has **presence** at a tier under these rules.

| Group | The person has presence at a tier when |
| --- | --- |
| Frequent | At least one third of this quarter's items of that type sit at that tier or higher |
| Rare | At least one item sits at that tier or higher |

Feature work is counted by weight and not by number. A T1 item counts 1, a T2 item counts 2 and a T3 item counts 4. So one High item outweighs several Low ones, and there are no story points that could be inflated.

The **working tier** of a dimension is the highest tier at which any of its evidence types has presence.

Example. An L2 closed 2 Low and 5 Medium feature work items. The weights are 2 × 1 + 5 × 2 = 12. Items at T2 or higher weigh 10, which is 83% of 12. That is more than one third, so there is presence at T2. No item is at T3, so there is no presence at T3. The working tier is T2.

One dimension has a stricter rule. For Engineering standards and system health at L3, two T3 items are needed, not one.

### 9.4 Confidence

Sometimes there is too little data to judge a person fairly. Confidence measures that. It has two inputs. Each input scores 1.0, 0.5 or 0.0, and the person's confidence is the lower of the two.

| Input | Scores 1.0 | Scores 0.5 | Scores 0.0 |
| --- | --- | --- | --- |
| Allocation on scored projects | 80% or more | 50% to 79% | Under 50% |
| Feature work items closed | 8 or more | 5 to 7 | Under 5 |

Confidence under 0.5 is low. When confidence is low, a dimension score of 0 becomes Not assessed (insufficient evidence). The reason goes to the project's hygiene report, because low confidence is a data problem and not a performance problem. A score of 1 or 2 stays as it is.

### 9.5 Lead-rated dimensions

For the 13 lead-rated-only dimensions there is no procedure. The Team Lead reads the statement at the person's level and gives the dimension score directly.

| The lead's judgment | Dimension score |
| --- | --- |
| The statement at the person's level is not met | 0 |
| The statement at the person's level is met | 1 |
| The person clearly goes beyond the statement at their level | 2 |

For a 0 or a 2, the lead must point to one evidence item as the reason.

### 9.6 The procedure as pseudo-code

```
function dimension_score(person, dimension, level, quarter):
    if dimension.is_lead_rated_only:
        return lead_score(person, dimension, level)

    items = evidence_items(person, dimension.evidence_types, quarter)

    if items.is_empty and not opportunity(person, dimension).possible:
        return NOT_ASSESSED

    if dimension.name == "Coding and code quality":
        items = apply_clean_delivery_rule(items)

    tier = highest working_tier(items of type t) over all t in dimension.evidence_types

    if tier < level:    score = 0
    elif tier == level: score = 1
    else:               score = 2

    for gate in dimension.gates:
        if gate(person, quarter) == FAR_MISS:
            score = 0
        elif gate(person, quarter) == CLOSE_MISS and score == 2:
            score = 1

    if confidence(person, quarter) < 0.5 and score == 0:
        return NOT_ASSESSED_INSUFFICIENT_EVIDENCE

    return score, together with items, tiers, gate results and notes
```

The pseudo-code leaves out two things to stay short. One is the "hardest work available" exception in step 5. The other is the lead's team-leadership question for Mentoring at L3.

### 9.7 Where people are involved

The AI judges. People take part in three places and nowhere else.

| # | Who | What they do | When |
| --- | --- | --- | --- |
| 1 | Team Lead | Corrects an evidence item when the AI got it wrong. The lead can change its tier, an AI label such as the work type, a "caused by" link, or who the item belongs to. Every change needs a written reason | Any time until the quarter is published |
| 2 | Team Lead. The EM does this for a lead's own profile | Scores the 13 lead-rated-only dimensions, the Collaboration row at L1, and the team-leadership question for Mentoring at L3 | Once a quarter, about 15 minutes per person |
| 3 | Developer | Adds work that no system recorded. This is self-added work, source 9. It counts only after the lead confirms it | Any time during the quarter |

These limits are part of the design.

- A developer cannot change or dispute an item, a tier or a score. A developer who sees a mistake tells their lead, and the lead decides.
- Nobody can change a dimension score directly. The lead corrects an item, and the system then calculates the score again.
- Nobody can add a dimension score that the system did not produce, except on the lead-rated rows.
- A developer sees only their own profile. A lead sees the profiles of their team. An EM sees the profiles of their developers.
- Each lead's scores on the lead-rated rows are summarized per quarter as counts of 0, 1 and 2. A lead who scores everyone the same becomes visible to the EM.

### 9.8 Protection against tier inflation

The score affects increment, so there is a natural pull to make work look bigger than it was. A lead could raise tiers, or tasks could be described as larger than they are. Three things work against that.

- **Every change is recorded.** The system keeps the AI's original tier next to the lead's tier, with the lead's reason.
- **The EM sees each lead's changes.** For each lead, the EM sees how many tiers were raised and how many were lowered in the quarter, and can open the reasons. A lead who raises many tiers and lowers none stands out.
- **Each project's spread of tiers is shown next to the company's spread,** so a project with unusually many High items stands out.

## 10. Step 6 — Build the score and the profile

### 10.1 The score

The score turns a person's dimension scores into one number. It is built in four small steps.

**1. Take the dimension scores.** Each scored dimension has a score of 0, 1 or 2 from Step 5. A dimension marked Not assessed is left out.

**2. Give each dimension its weight** for the person's level, taken from settings. Section 8.1 explains the weights. A dimension with a weight of 0 is left out.

**3. Calculate two part-scores with the same formula.** One part uses the evidence-backed dimensions. The other part uses the lead-rated dimensions. Each part is the weighted average of its dimension scores.

```latex
S_{\text{part}} = \frac{\sum (\text{dimension score} \times \text{weight})}{\sum \text{weight}}
```

**4. Combine the two parts.** Evidence decides most of the score at every level.

```latex
S = 0.7 \times S_{\text{evidence}} + 0.3 \times S_{\text{lead}}
```

**No score is published when the evidence part is empty.** If no evidence-backed dimension could be scored, the person gets "No score this quarter (insufficient evidence)" and no number. A score is never built from lead input alone. The lead's scores are still shown on the profile, marked as lead-rated only, and the project's hygiene report says why the evidence was missing.

If only the lead-rated part is empty, the evidence part is the whole score.

How to read the score.

- The range is 0 to 2. A dimension score of 1 on every scored dimension gives exactly 1.0.
- The same formula, limited to one growth area, gives six sub-scores. They show the shape of the quarter.
- **The score compares people of the same level only.** An L1 and an L3 have different statements and can have different weights, so their scores cannot be compared. Section 10.4 describes the list the EM can use.
- **The score is for performance and increment decisions.** The system gives no overall label to a person, such as "meets expectations". Where the line for a good score sits is a decision for management. Level change is a separate judgment and is not part of this system.

### 10.2 The profile

The profile is the one page that the developer, their Team Lead and their EM all read. It is frozen when it is published, and one profile is kept per quarter.

| Section of the profile | What it shows |
| --- | --- |
| Header | Name, level, quarter, projects and allocation, the score, confidence, and the hygiene % of the person's projects |
| Work level over time | The main chart. Section 11 describes it |
| Six growth-area bars | For each growth area, the sub-score and the number of dimensions at 0, 1, 2 and Not assessed |
| Strengths and gaps | Dimensions that scored 2. Dimensions that scored 0, each with the tier the work sat at, the gate that failed if any, and the items. Every dimension has a "next step" line |
| Dashboard analytics | All analytics from Step 3. The five quality gates are shown in their own panel, each as pass, close miss or far miss |
| Opportunity | What the project offered against what the person took, and any staffing flag |
| Lead-rated rows | The lead's score and the item the lead pointed to. They are shown apart from the evidence-backed scores so the two are never confused |
| Trail | Every dimension score opens to its items, their tiers, the gate results, and any correction a lead made with its reason |

### 10.3 Three reports for management

Three things leave the profile and go to management. All of them are about projects, not about people.

- **Staffing flags** go to the EM and the CTO, per project. Example: "This project is not offering High work to its L3s."
- **The hygiene report** goes to each project. It shows the share of PRs linked to a work item, bugs with a "found in" stage, sprint moves with a reason, and releases marked.
- **Best-practice flags** go to the EM and the CTO, per project. A flag is raised when a whole project falls below a DSi standard, such as tests with features. Example: "This project shipped 30% of its features with tests. The standard is 90%." The flag is about the project. No developer's score is lowered because of it.

Poor hygiene is reported on the project. It is never read as poor performance of a person.

### 10.4 The EM's list by level

The EM can open a list of all developers of one level, sorted by score. This shows who is doing better within that level.

- The list shows each person's score, the six sub-scores and the confidence.
- A list holds one level only. L1, L2 and L3 are never mixed.
- A person with low confidence is marked, because that score rests on little data.
- The system does not draw a line in the list. The EM and management decide which scores they consider good, acceptable or weak.

* A person who has no score for the quarter is not ranked. They are listed separately, with the reason.

## 11. Keeping the dashboard and the score in agreement

There is a risk in showing analytics that are not scored. A developer could see a busy dashboard with many closed tasks and many reviews, and next to it a low score. That would be confusing and would feel unfair.

The design answer is that the score is one view of the dashboard and never a second opinion. Both are calculated from the same evidence items with the same tiers. When they seem to disagree, the dashboard must show the reason at one glance. Four features make this happen. None of them adds a new threshold, a new label or a new input from people.

### 11.1 The "Work level over time" chart

This is one line chart at the top of every profile. It shows whether the person's feature work sits at their level, month by month.

| Line on the chart | What it shows | Calculated from |
| --- | --- | --- |
| Solid line, the person | Share of the person's weighted feature work at their level's tier or above | The person's feature work items, with their tiers and the weights 1, 2 and 4 |
| Thin line, the person | The same share, at the next tier or above. For an L2 this is High or above | The same items |
| Dashed line, the expectation | One third. This is the presence rule from 9.3, drawn as a line | A setting |
| Grey line, the project | Share of the project's weighted closed feature work at the person's level tier or above | All feature work items closed on the project during the person's allocation |

Each point on the chart covers the last three months. So the last point of the quarter is exactly the number that Step 5 uses for Planning and execution. The chart and the dimension score cannot disagree, because they are the same calculation. Three months also keeps the line from jumping when a month has only two or three items.

| What the chart shows | What it means |
| --- | --- |
| Solid line above the dashed line | Planning and execution scores 1, if the gates pass |
| Thin line above the dashed line | Planning and execution scores 2, if the gates pass |
| Solid line below the dashed line, and the grey line also below | The project did not offer enough work at the level. Staffing flag, no penalty |
| Solid line below the dashed line, and the grey line above | The gap is the person's. The trail shows which work was taken |
| Hollow point | Fewer than three items in the window. The point is drawn but not read |

### 11.2 The same chart for every evidence-backed dimension

Opening a dimension shows its own version of the chart. There are two variants and no others.

| Evidence group | Chart | Scores 1 or more when |
| --- | --- | --- |
| Frequent types (Feature work, Review comment written, Test change) | Share at the level's tier or above, per month, against the one-third line | The line is at or above the one-third line |
| Rare types (all others) | A tier timeline. Each item is one dot, placed at the height of its tier, with months across and the person's level drawn as a line | At least one dot is on or above the level line in the last three months |

### 11.3 A "next step" line under every dimension score

Under each dimension score the profile shows what work at the next tier would look like. The line is generated from the tier table in 6.4 and is never written by hand. It turns a score into a next step.

- "Design and architecture: 1, works at T2. To score 2: a Design task with options compared, or an ADR."
- "Planning and execution: 1, works at T2. To score 2: a third of weighted feature work at High."

### 11.4 A consistency check before anything is published

Before profiles are published, the system lists every person whose dashboard analytics are in the top quarter of their level but who scores 0 on any evidence-backed dimension. Each case is one of two things. Either it is the intended case of busy, low-tier work. Or it is a design fault in the tier table or in a gate. The list goes to the EM for review. It never goes to the person.

## 12. Worked example: one SWE L2 through one quarter

The name and all numbers are made up. Rafi is an SWE L2 on a government project, allocated 100%.

### 12.1 What the project offered and what Rafi took

| Part of the opportunity profile | Value |
| --- | --- |
| Feature work items closed on the project | 11: 3 Low, 6 Medium, 2 High |
| Rafi's own feature work items | 7: 2 Low and 5 Medium |
| Client exposure | Yes, two UAT sessions |
| Juniors present | Yes, one intern |
| Pre-sales on the project | No |
| HR invitation | No |

### 12.2 Confidence

| Input | Value | Score |
| --- | --- | --- |
| Allocation | 100% | 1.0 |
| Feature work items closed | 7 | 0.5 |

The lowest score is 0.5. That is not under 0.5, so confidence is normal.

### 12.3 Dimension scores for the evidence-backed dimensions

| Dimension | Items and tiers | Working tier | Gates | Dimension score |
| --- | --- | --- | --- | --- |
| Coding and code quality | Feature work after the clean-delivery rule: 4 Medium delivered clean (T2, weight 8), 1 Medium with a High-severity QA bug (drops to T1, weight 1), 2 Low (T1, weight 2). So 8 of 11 weighted units are at T2, which is 73%. Review comments: 14 in total, 3 at T1, 9 at T2, 2 at T3. So 79% are at T2 or higher and 14% are at T3 | T2 | None | 1 |
| Design and architecture | Design notes on 2 own features (T2) | T2 | None | 1 |
| Engineering standards and system health | Test changes: 7 unit (T1), 2 integration and 2 regression (T2). So 36% are at T2. Bug fix: 1 production bug with root cause recorded (T2). Refactor: 2 small ones on own initiative (T2) | T2 | G1: 7 of 7, pass. G2: 2 of 2, pass. G4: pass | 1 |
| Planning and execution | Feature work with plain tiers: 10 of 12 weighted units at T2, which is 83%. None at T3. Planning items: own features broken into tasks with estimates (T2) | T2 | G3: 90%, pass | 1 |
| Collaboration and negotiation | Comments on 2 other people's Design tasks (T2) | T2 | None | 1 |
| Mentoring and people leadership | A formal assignment to mentor the intern for the quarter (T2) | T2 | None | 1 |
| Knowledge sharing | Feature and API docs for own work (T2). No sessions | T2 | None | 1 |
| Hiring and onboarding | No items. No new joiner and no HR invitation | None | None | Not assessed |
| Pre-sales and business growth | No items, and none were possible | None | None | Not assessed |
| Client delivery, proposed | 5 UAT issues on own feature work resolved (T1). Handover notes on 2 features (T2) | T2 | None | 1 |

Notice the Coding row. Rafi's two T3 review comments are 14% of his comments. That is under one third, so they do not give presence at T3. They stay visible on the trail as the start of L3-kind work.

### 12.4 Lead-rated dimensions

The Team Lead gives a score of 1 on all 13 lead-rated-only dimensions. No item needs to be pointed to, because every score is 1. The system showed the lead two things as context: Rafi demoed one of his own features, and he used a message queue for the first time on this project.

### 12.5 The score

The example uses the proposed weights for L2 from section 8. Two evidence-backed dimensions are Not assessed, so they are left out. Rafi scores 1 on every other dimension.

| Part | Dimensions counted | Total weight | Weighted sum of dimension scores | Part-score |
| --- | --- | --- | --- | --- |
| Evidence-backed | 8 | 19 | 19 | 1.0 |
| Lead-rated | 13 | 20 | 20 | 1.0 |

```latex
S = 0.7 \times 1.0 + 0.3 \times 1.0 = 1.0
```

### 12.6 What the profile says

Rafi's score for the quarter is 1.0. He works at T2 on every scored dimension, which is what the L2 statements describe.

Planning and execution scores 1 even though Rafi had none of the two High items. They went to the L3 before the sprint started. For an L2, High feature work is the way to a score of 2. It is not needed for a score of 1. The gap still shows on the opportunity view as "High offered: 2, taken: 0".

If next quarter one third of Rafi's weighted feature work is High and no gate fails, Planning and execution scores 2 in that quarter.

## 13. How a quarter runs

### 13.1 A normal quarter, in order

1. **Every day.** The system pulls new records, creates evidence items, sets their tiers with AI and updates the dashboard. These are Steps 1 to 3.
2. **Any time.** A Team Lead corrects an item where the AI got it wrong, with a written reason. A developer adds work that no system recorded, and the lead confirms it.
3. **Mid-quarter.** Each developer and lead sees a preview of the draft profile. A developer who sees a mistake or missing work tells their lead while there is still time.
4. **Quarter close.** The evidence items of the quarter are frozen.
5. **Within one day after close.** The system calculates the dimension scores. This is Step 5.
6. **About two weeks after close.** Leads make their last corrections and score the lead-rated rows. A corrected item makes the system calculate the dimension score again.
7. **Before publishing.** The EM reviews the consistency list from 11.4 and each lead's tier changes from 9.8.
8. **Publish.** Profiles are published and frozen. This is Step 6.

### 13.2 The first quarter is a shadow quarter

In the first quarter the system runs fully, but nothing is published. Scores are calculated and kept internal.

In this quarter each Team Lead checks a sample of the AI's tiers on their own project. The purpose is to see how often the AI is right, to compare the scores with what leads would have said, to find faults in the tier table and the gates, and to bring project hygiene up.

After the shadow quarter, all settings in section 14 are set again from the real data.

### 13.3 The six habits every project must follow

The system can only be fair if projects keep their records well. These six habits are measured per project as a hygiene %.

1. Every bug is linked to its feature and has a "found in" stage.
2. Every sprint move has a reason.
3. Every task has an estimate, and every task is linked to its feature.
4. A feature that needs real design has a child Design task, with the "options considered" field filled.
5. A feature is marked "Released" when the project has no CI/CD to give the release date.
6. The OpenProject id is in every branch name or commit message.

### 13.4 Adding things later

A new evidence type or analytic that needs new tracking fields, new AI judgment or a long history stays out until the base system has run for two quarters. The mapping in Step 4 is reviewed once a year.

## 14. Settings

No number in this spec is hard-coded. Each one is a setting. The EM or the Director sets the values. Every setting has a version per quarter, so an old score can always be explained with the values that were in force.

The values below are starting values.

| Setting | Starting value | Used in |
| --- | --- | --- |
| Presence threshold for frequent evidence types | One third of items | 9.3 |
| Feature work weights by tier | T1 = 1, T2 = 2, T3 = 4 | 9.3 |
| T3 items needed for Engineering standards at L3 | 2 | 9.3 |
| G1 Tests with features | Pass within 10 points of the project, far miss beyond 25 points. The project standard for the flag is 90% | 7.3 |
| G2 Regression tests | Pass within 10 points of the project, far miss beyond 25 points. The project standard for the flag is 90% | 7.3 |
| G3 Sprint commitment | Pass at 85%, far miss under 70% | 7.3 |
| G4 Escaped defects | Pass within 10 points of the project, far miss beyond 25 points | 7.3 |
| G4 small-project limit | Fewer than 3 developers or fewer than 15 feature work items | 7.3 |
| G5 Knowledge spread | At least 2 other active contributors per owned module | 7.3 |
| Production bug window | 30 days after release | 5 |
| Ownership period | 6 months or more as the owner | 6.4 |
| Confidence: allocation | 1.0 at 80%, 0.5 at 50% | 9.4 |
| Confidence: feature work items closed | 1.0 at 8, 0.5 at 5 | 9.4 |
| Low-confidence limit | Under 0.5 | 9.4 |
| Dimension weights | One number per dimension per level. Proposed by this spec and confirmed by management. A weight of 0 means not counted | 10.1 |
| Score split | 70% evidence-backed, 30% lead-rated | 10.1 |
| Chart window | Last three months | 11.1 |
| Smallest number of items for a chart point to be read | 3 | 11.1 |

## 15. Developers on client-owned projects

Some developers work on projects where the client owns the code and the tracker. Most foreign client projects are like this. DSi cannot read that code, so there are no PRs, review comments or test changes to read. These developers are scored on a separate track with its own sources and its own score split.

This section describes only what is different. Everything it does not mention works as in sections 5 to 14. The four rules in section 2 apply here without change.

### 15.1 The idea of this track

Sections 5 to 14 judge work by reading the code. This track judges work by reading the ticket. Three things replace what the code would have shown.

- **The ticket** shows what the work was and how hard it was.
- **The client** says how good the work was, through a short quarterly pulse.
- **The DSi Team Lead** on the client project scores what neither of the two can show.

### 15.2 Two modes for getting the work records

Each client-owned project is placed in one mode. The EM sets the mode after the client's agreement is checked. There are two modes, and the only question is whether the client agrees to share its ticket data.

| Mode | What DSi receives | How it arrives |
| --- | --- | --- |
| A. The client agrees | The developer's stories from the client's Jira: text, type, story points, status, dates, sprint history, reopens and linked bugs | The system reads the client's Jira directly. Where the client allows a copy but not direct access, the Team Lead exports each sprint and uploads it |
| B. The client does not agree | No client data | The developer fills a sprint work record at the end of each sprint, and the Team Lead confirms it |

**The confidentiality rule.** A client's ticket text is the client's information. Ticket descriptions, comments and attachments are never copied into DSi systems without the client's written permission. Mode A needs that permission. When the client does not give it, the project runs in mode B. If the client allows only some fields, such as points and status but not the ticket text, the project still runs in mode A and the developer adds a short description of each story in their own words.

### 15.3 The sprint work record

The sprint work record is an OpenProject item that the developer fills once, at the end of each sprint. It has one line per story the developer finished. It is used in mode B. In mode A it is not needed, because the same information comes from the client's Jira.

| Field | Meaning |
| --- | --- |
| Description | Two or three lines in the developer's own general words. Example: "Added a payment provider integration with retry handling" |
| Work type | One of the 13 work types from 6.2 |
| Story points | The points the client's team gave the story |
| What made it hard | One line. Example: "New integration, unclear requirements" |
| Committed in this sprint | Yes or no. It says whether the story was part of the sprint plan |
| Came back | Yes or no. It says whether the story was reopened or had a bug returned by the client |
| Reference | The client's ticket key and PR number, only when the client allows these to be noted |

Three rules apply to the record.

- **Own words only.** The developer never pastes text from the client's ticket or code.
- **Once per sprint, not daily.** This keeps the rule "no daily entry by developers" from section 1.
- **Confirmed within five working days.** The Team Lead confirms each record against the sprint he worked in. A confirmed record is an evidence item. An unconfirmed record counts for nothing.

### 15.4 How the tier is set without code

The evidence type is still Feature work, and the tier table in 6.4 is still the reference. Only the input changes.

**The unit.** When the story's epic is known, the unit is the developer's stories under that epic, judged together as in 6.2. When it is not known, each story is one feature work item.

**Point bands.** Story points mean different things in each client team. So the Team Lead sets point bands once per team, and the EM can see them. Example: 1 to 2 points is Low, 3 to 5 is Medium, 8 or more is High.

**The AI's judgment.** The AI sets the complexity from the point band, the description, the work type and the "what made it hard" line. In mode A it also reads the ticket text. Every item is marked "judged without code". The Team Lead can change the tier with a written reason, as in 6.3.

**Points are never added up.** Rule 1 still holds. Story points only help to place an item in a band. Feature work is counted with the weights 1, 2 and 4 from 9.3, and a total of story points is never shown as a measure of a person.

### 15.5 How each dimension is scored on this track

The dimensions and their weights are the same as in section 8. The table shows where the dimension score comes from on this track.

| Dimension | On this track | Details |
| --- | --- | --- |
| Coding and code quality | Evidence-backed, from Feature work | Review comments are not read. The clean-delivery rule in 9.2 uses reopens and linked bugs in mode A, and the "Came back" field in mode B |
| Planning and execution | Evidence-backed, from Feature work | Gate G3 uses stories done against stories committed in each sprint. Gate G5 does not apply, because module ownership cannot be seen |
| Engineering standards and system health | Lead-rated | No tool can see tests, refactoring or security work here. The lead sees the client pulse answer on quality as context. Gates G1, G2 and G4 are noted as "gate unverified" |
| Design and architecture | Evidence-backed only through self-added work | A design the developer wrote for the client is added as self-added work and confirmed by the lead. Without one, the dimension is Not assessed |
| Collaboration and negotiation | Lead-rated | The lead works inside the client's team and sees this directly |
| Mentoring, Knowledge sharing, Hiring and onboarding, Pre-sales | Same as section 8 | These records are DSi's own, so nothing changes |
| Client delivery, proposed | Same as section 8 | Demos and handovers the developer gave are added to the sprint work record and confirmed by the lead |
| The 13 lead-rated-only dimensions | Same as section 8.5 | Nothing changes |

Rule 4 applies in full. A dimension that the client's setup makes impossible to show is Not assessed. It is never a 0.

### 15.6 The client pulse

On a client-owned project the client's own people review the developer's code. They are the only ones who can speak about its quality. The client pulse asks them, in a form short enough to answer in two minutes.

Once a quarter the client-side manager of the developer answers three questions. Each answer is 0, 1 or 2, with the same meaning as a dimension score: below what they expect, what they expect, or clearly more.

| # | Question | What it stands in for |
| --- | --- | --- |
| 1 | How is the quality of this developer's work? | Review findings, tests and escaped defects |
| 2 | Does this developer deliver what they commit to? | Sprint commitment |
| 3 | How well does this developer communicate with your team? | Collaboration |

The pulse score is the plain average of the three answers. When the client does not answer, the pulse part is left out of the score and its share is divided between the other two parts in proportion. The yearly client feedback that DSi already collects is shown on the profile as context and is not scored.

### 15.7 The score on this track

The score has three parts on this track. Each part is a weighted average on the 0 to 2 scale, as in 10.1.

| Part | Sections 5 to 14 | Mode A, the client agrees | Mode B, the client does not agree |
| --- | --- | --- | --- |
| Evidence-backed | 70% | 50% | 40% |
| Client pulse | None | 20% | 25% |
| Lead-rated | 30% | 30% | 35% |

These are starting values and are kept as settings. The evidence part is smaller here because less can be seen. It is smallest in mode B, because there the evidence rests on the developer's own record.

Three rules from 10.1 stay as they are.

- No score is published when the evidence part is empty.
- The score compares people of the same level only.
- The system gives no overall label to a person.

One rule is added. The EM's list in 10.4 is kept separately for this track. A score built from tickets and a score built from code are not sorted into one list.

### 15.8 The profile and the dashboard

- **A badge in the header.** It names the track and the mode, for example "Client-owned project, mode B". Everyone who reads the score knows what it rests on.
- **Fewer analytics.** The dashboard shows Work classification, Completed work, Work complexity and Delivery outcome from the tickets. Issues on own work is shown in mode A. All analytics that come from DSi's own records are shown as usual.
- **Missing is not zero.** Analytics that need code, such as Review participation and Testing contribution, show "Not available on this project". They never show 0.
- **Work level over time.** The chart in 11.1 is drawn from the ticket-based feature work items. The grey project line is drawn only in mode A, where the whole project's stories are known.

### 15.9 Safeguards

This track leans more on people, so it needs its own protection against scores that are too kind.

- **The lead's confirmations are visible.** As in 9.8, the EM sees how many records each lead confirmed, changed or raised in tier, and how late they were confirmed.
- **Spot check by reference.** Where the record carries the client's ticket key or PR number, the EM can ask the lead to open a sample in the client's system and confirm the record matches.
- **An outside voice.** The client pulse does not come from the lead. A lead who scores a developer high while the client scores them low stands out to the EM.
- **Hygiene for this track.** The project's hygiene % counts three habits: a sprint work record exists for every sprint, it is confirmed within five working days, and point bands are set for the team.

## 16. Open points

These points are not decided yet. Each one needs an owner and an answer before or during implementation.

### To confirm with the EM and the Director

- [ ] The weight of each of the 22 dimensions at L1, L2 and L3.
- [ ] The 70 / 30 split between the evidence-backed part and the lead-rated part of the score.
- [ ] The one-third presence rule and the feature work weights 1, 2 and 4.
- [ ] The G4 values of 10 and 25 points, and the small-project limit.
- [ ] **Values for G1 and G2.** The 10 and 25 point limits against the project, and the 90% project standard that raises the best-practice flag.
- [ ] When SWE L4 joins the system after the pilot, and what a dimension score of 2 means at the highest level.

### To raise with the Career Framework team

- [ ] The proposed Client delivery dimension. Does it stand alone or join an existing growth area? Until that is answered, does it count in the score?

### To find out

- [ ] Which client delivery activities are actually recorded today (source 5).
- [ ] Whether a pre-sales repository exists (source 6), and who owns it.
- [ ] Who looks after the identity map day to day.
- [ ] How long leads have for corrections after quarter close. This spec only says "about two weeks".
- [ ] **Formal mentoring record.** Set up the mentoring assignment in the HR and learning records, and decide who creates it. The mentee feedback form is future scope.

### To decide during implementation

- [ ] **Anchor rule.** Should one main evidence type set a dimension's working tier? Without it, one rare item can lift a dimension that has several evidence types. For example, one T3 Planning item can lift Planning and execution above what the Work level chart shows.
- [ ] **A minimum delivery floor for L1.** Should an L1 need a minimum amount of delivered work before Coding and Planning can score 1?
- [ ] **The "hardest work available" exception in 9.1.** The exact rule for what counts as taking or declining the work needs to be written down.
- [ ] **The number of analytics.** Section 7.2 now lists 33 analytics. Delivery cycle time and Cross-module work were removed on purpose. Recheck the list once more before implementation.
- [ ] **Self-added work.** It is kept for now, so that work no system can capture is not lost. Decide later whether it stays as it is.
- [ ] **How well the AI sets tiers.** The lead no longer confirms each item, so the AI's accuracy matters more. The shadow quarter must show how often leads had to correct it.
- [ ] **Module ownership from Git.** Two details are still open. One is what counts as a module in each repository. The other is the smallest share of changes and reviews that makes someone the proposed owner.
- [ ] **Feature work that runs across quarters.** A feature work item counts when the feature closes. Decide what happens when a feature stays open for more than one quarter.
- [ ] **Confidence value for feature work.** The value of 8 closed items was chosen when the unit was a story. It must be set again from real data.

### For the client-owned project track

- [ ] **Agreement check.** For each foreign client, check the confidentiality and data-handling clauses, and decide who asks the client for written permission for mode A.
- [ ] **Client pulse.** Find out whether clients will answer three questions every quarter, and who at DSi sends them.
- [ ] **Score split.** Confirm the 50 / 20 / 30 and 40 / 25 / 35 splits with the EM and the Director.
- [ ] **Point bands.** Decide whether the lead sets them alone or the EM approves them.
- [ ] **A developer on both kinds of project in one quarter.** Decide which track applies, or how the two are combined.
