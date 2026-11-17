# FNN Feature name: design

State: draft | approved on YYYY-MM-DD

## 1. Goal

One or two sentences. What exists after this feature that did not exist before, and who sees it.

## 2. Scope

**In.** What this feature builds.

**Out.** What it does not build, and which feature does.

## 3. Rules from the spec and the PoC plan

Copy every table and rule this feature uses, in full, with its source (`spec 6.2`, `poc-plan.md 3.2`). Where the PoC differs from the spec, say so and give the reason.

## 4. What the real data looks like

Facts this design rests on, each with the date it was checked and how (the API call or the record looked at). Include one or two real examples.

## 5. Behaviour

What the feature does, step by step. Inputs, outputs, the rule for each decision.

## 6. Data

Tables and columns created or changed, with types and meaning. For JSONB columns, the shape of the content. What is never updated.

## 7. Interfaces

**Uses** (from earlier features): exact class, method and table names.

**Gives** (to later features): exact class, method, endpoint and table names, with types.

## 8. Cases that must work

The unusual inputs and failures, and what happens for each. Examples: an empty result, a record seen twice, a call that fails halfway, a very large input.

## 9. How it is run

The command or endpoint that starts it, its parameters, and what it prints.

## 10. How it is checked

The acceptance checks from the master plan's "Done when", made exact: the command or the manual check, and the expected result.

## 11. Decisions made in this deep dive

Each open question from the master plan, with the answer and the reason. These are also added to `docs/decisions.md`.

## 12. Still open

Anything not decided, and who decides it.
