# Product

## Register

product

## Platform

web

## Users

Three roles, all equally primary: **developers** log daily work in short structured entries (category, status, description, technologies, optional hours); **leads** review and approve submitted days, keep private per-developer notes, and watch their team's blockers; **managers** get org-wide visibility across every team and administer the allowlist, teams, work types, and technology list. Each role has its own home screen (`/today`, `/team`, `/org`), and each deserves equal design attention — no single audience dominates the other two.

## Product Purpose

Engineering Intelligence replaces a manual Excel workflow for tracking daily engineering work. Developers log tasks as they go; leads approve and lock days, keeping private notes leads/managers can see but the developer being noted about never can; leads and managers get category/technology heatmaps and a live blocked-tasks view across their teams. Authorization is enforced primarily through Postgres Row Level Security, so the visibility rules (a developer never sees another developer's logs or any note about themself) are guarantees, not conventions.

Success is: developers log consistently because it's low-friction, not because they're chased; leads/managers get faster, clearer visibility into blockers and where time is actually going; the result is an accurate, auditable historical record that's more trustworthy than a spreadsheet — and the whole thing has to feel easy and pleasant to use, not like another form to fight through.

## Positioning

The single, low-friction system of record for daily engineering activity — trustworthy enough to drive approvals and reviews, fast enough that developers actually keep it up every day.

## Brand Personality

Warm, approachable, human. This is a tool that logs what people worked on and surfaces it to their lead and manager — the exact shape of thing that can feel like surveillance if it reads cold or clinical. The personality has to work against that: friendly, encouraging, never punitive, even in the approval and oversight views. No named external reference was given; the direction is defined relative to what to avoid (below) plus the existing sky/azure "telemetry" palette already in `globals.css`, which should soften toward this warmth rather than being replaced outright.

## Anti-references

Explicitly not Jira- or Workday-shaped: dense, cluttered, form-heavy enterprise software that feels like a chore to open. Avoid anything that reads as a compliance tool first and a daily-use tool second.

## Design Principles

- **Two-minute logging, always.** Daily entry is the make-or-break interaction — any friction here is friction the whole product doesn't recover from.
- **Warmth over authority.** Every screen a lead or manager uses to review someone's work should still read as supportive, not surveillance. Tone and layout choices should avoid feeling like an audit trail.
- **Equal craft across roles.** The developer's daily-entry screen gets the same design attention as the manager's org dashboard — neither is the "real" product with the other bolted on.
- **Trustworthy at a glance.** Approval status, blockers, and history need to read clearly without over-explaining; this is the record people rely on for reviews.
- **Private stays private, visibly.** The dev-notes visibility rule (never shown to the developer it's about) is a hard product guarantee — the UI must never leak its existence, not just its content.

## Accessibility & Inclusion

No formally specified WCAG level was requested; default to standard AA-equivalent good practice — adequate contrast, color never the sole signal (already true of the existing pill/heatmap design), and reduced-motion support for any added animation.
