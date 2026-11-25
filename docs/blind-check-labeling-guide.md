# Blind check: labeling guide

You label 30 Feature work items with a complexity, without seeing the AI's answer. Afterwards you read the AI's reason for each and mark it. About 3 hours for the labels.

## The order that must be kept

1. Until your labels are imported, do not open a developer's profile, the developers list, the compare page or the overview. They show complexities.
2. The item page of a sampled item hides the AI's answer until its label is imported. Other item pages do not: leave them closed too.
3. A label is given once. After the import it cannot be changed, because from then on the AI's answer can be seen.

## Steps

1. Open `localhost:3000/blind-check` and download the file.
2. Open it in a spreadsheet. Each row is one developer's part of one feature.
3. For each row: read the work item and every pull request in the row. Fill in `complexity`. Write a `note` when you want to say why; a note on every item you find hard to place helps the report.
4. Save as CSV and upload it on the same page. Rows with an empty `complexity` are skipped, so you can upload in parts. A wrong value stores nothing; the page lists every wrong row.
5. Open each item from the page. It now shows your label beside the AI's judgment. Mark whether the AI's reason is right, and for each problem whether its depth is right. Save.
6. When every reason is marked, tell the session. It checks the numbers and writes `docs/feasibility-report.md`.

## What to fill in

| Column | You write |
| --- | --- |
| `complexity` | `low`, `medium`, `high` or `very_high`. Or `cannot_judge` when the sources do not let you decide. Capitals and spaces do not matter |
| `note` | Anything, or nothing |

Leave the other columns as they are.

## How to judge (spec v1.6 section 6.2)

Judge the part of the developer named in the row: their tasks under the feature and their pull requests, together. Complexity comes from the problems the work had to solve and how hard each was. It does not come from the size of the change, the number of modules or the time spent.

**Move 1.** List the distinct problems in the work.

**Move 2.** Give each problem a depth.

| Depth | Meaning | Example |
| --- | --- | --- |
| Routine | The project already has a solved example of this problem, or it is standard practice that every developer on that stack knows | A create, read, update and delete screen. A second datatable built like the first one |
| Answer exists | The tool or a well-known pattern covers the problem, but the project had no earlier example. The person had to find the answer and apply it | Streaming a large file in a tool that supports streaming. Storing hashes to discard duplicates |
| No ready answer | The tool does not support it and no known pattern fits. The person had to do R&D and work out a solution | A map that drills down from country to district in a dashboard tool that has no such chart |

**Move 3.** Set the complexity from the depths.

| Complexity | When it applies |
| --- | --- |
| Low | Every problem is routine |
| Medium | The deepest problem is "answer exists" |
| High | At least one problem has no ready answer. Or several "answer exists" problems affect each other. Or the work is a design whose mistakes stay hidden across the system, such as a permission model |
| Very high | The problems reach across projects or the organization |

Two problems affect each other when two different people could not have built and tested them separately.

## The five reference examples

| Feature work | Problems and their depth | Complexity |
| --- | --- | --- |
| Organization create, read, update and delete | Form, validation, save and list. All are routine | Low |
| First datatable on the project, with a proper query | Table with paging, sorting and search on the server: answer exists. Write the query well: answer exists | Medium |
| Role-based permission design | Role and permission model: answer exists. Limit each user to their own organization's data: answer exists. Enforce it on every screen and API: answer exists, and mistakes stay hidden. The three affect each other | High |
| File ingestion through NiFi, built from scratch | First flow on the stack, streaming large files, hashes to discard duplicates, metadata when NiFi does the insert: each answer exists. They affect each other, and mistakes stay hidden | High |
| Clickable map with drill-down in Superset | Click to drill down: no ready answer. Marts per area level: answer exists. Coverage query: routine | High |

## The rules

- **Size is not complexity.** A 2,000-line generated file is Low. A few lines that turn a synchronous method into an asynchronous one and remove a serious problem can be High.
- **The first solution gets the credit.** Once the project has a solved example, the same problem is routine for the next person.
- **New to the person is not new to the project.** Only the project's lack of an earlier example counts.
- **Merged means accepted.** Judge what was merged, not whether a simpler solution existed.
- **The work must leave a trace.** Work with no trace cannot be judged: that is `cannot_judge`.
- **Complexity does not depend on how the tickets were cut.**

## Where the AI had less than you

Label by the rules above, with the repository open. The AI worked under two relaxations, and the report shows what they cost:
- It did not search the repository for an earlier example. It assumed there was none, so it calls a problem routine only when it is standard practice of the stack (decisions 303, 320).
- It saw the tickets and a digest of each pull request, not the full code.

## Marking the AI's reason

For each problem the AI listed, choose one: the depth is right; it should be routine; it should be answer exists; it should be no ready answer; it is not a real problem of this work. Then say whether the reason as a whole is right: the problems are the real ones and the facts it cites are true. A right complexity with a wrong reason is marked wrong.
