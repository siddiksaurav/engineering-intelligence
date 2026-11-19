# FNN Feature name: design

State: draft | approved on YYYY-MM-DD, through the question brief

## Goal

One or two sentences. What exists after this feature that did not exist before, and who sees it.

## In scope / Out of scope

**In.** What this feature builds.

**Out.** What it does not build, and which feature does.

## Data and tables

Tables and columns created or changed, with types and meaning. For JSONB columns, the shape of the content. What is never updated. New rows in `setting`.

Copy every spec table and rule this feature uses, in full, with its source (`spec 6.2`, `poc-plan.md 3.2`). Where the PoC differs from the spec, say so and give the reason.

Facts about the real data this design rests on, each with the date it was checked and how (the API call or the record looked at). Include one or two real examples.

## Main flows

What the feature does, step by step: inputs, outputs, the rule for each decision. The command or endpoint that starts it and its parameters.

**Uses** (from earlier features): exact class, method and table names.

**Gives** (to later features): exact class, method, endpoint and table names, with types.

## Edge cases

The unusual inputs and failures, and what happens for each. Examples: an empty result, a record seen twice, a call that fails halfway, a very large input.

## How it will be tested

The acceptance checks from the master plan's "Done when", made exact: the command or the manual check, and the expected result. The architecture tests this feature enables.

## Open points

While in draft: the question brief (business questions, technical decisions for the user, decisions made by the session). Once approved, this section is named "Decisions" and lists every decision with its number in `docs/decisions.md`; no open point is left.
