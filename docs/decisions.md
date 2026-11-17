# Decisions

Every decision that shapes the PoC, newest at the bottom. A later entry overrides an earlier one; when it does, it names the entry it replaces. Add an entry whenever the user decides something or a feature deep dive closes an open question.

| # | Date | Decision | Reason | Source |
| --- | --- | --- | --- | --- |
| 1 | 16 Nov 2026 | The PoC uses public data: `github.com/opf/openproject` and `community.openproject.org` | Real, rich data that can be shown to management without access to DSi projects | `poc-plan.md` Context |
| 2 | 16 Nov 2026 | Scope is option B: judgment plus a profile slice. Option C comes later | The smallest scope where the whole chain from source record to dimension score is visible | `poc-plan.md` 2 |
| 3 | 16 Nov 2026 | No screen for a lead to correct a judgment in the PoC. The data model supports corrections | Keeps the PoC small; the screen can be added without table changes | `poc-plan.md` 3.1 |
| 4 | 16 Nov 2026 | The accuracy measurement is optional and comes after all planned features | Nothing in F01 to F12 depends on it | `poc-plan.md` 4 |
| 5 | 16 Nov 2026 | The demo is planned separately | Not part of the build | `poc-plan.md` Context |
| 6 | 16 Nov 2026 | Real developer names are shown | The data is public | `poc-plan.md` Context |
| 7 | 16 Nov 2026 | Any cloud LLM may be used, behind the `JudgmentModel` interface. Start with a Claude model | The provider can be changed without touching the rest | `poc-plan.md` 3.2 |
| 8 | 16 Nov 2026 | Stack: PostgreSQL, Spring Boot, Next.js | User's choice | `poc-plan.md` Context |
| 9 | 17 Nov 2026 | Code written with AI belongs to the person who pushed it. The AI-usage labels on PRs are ignored | Decided by the user at the plan review; reason not recorded | Plan review |
| 10 | 17 Nov 2026 | A bug is never Feature work. The tracker type decides, not the AI | Decided by the user at the plan review; reason not recorded | Plan review |
| 11 | 17 Nov 2026 | The second dimension is Engineering standards and system health, not Planning and execution | Its evidence (tests, refactor, security, infra) is present in the data; sprint data is not | Plan review |
| 12 | 17 Nov 2026 | A 30-item blind check is added at build step 8 (F08) | A first check of the AI before scores are built on it | Plan review |
| 13 | 17 Nov 2026 | Java 25, not Java 21 | User's decision | This session |
| 14 | 17 Nov 2026 | Spring Boot 4.x, not 3 | Current major line with Java 25 as a first-class target; longer support for a system meant to become the real product | This session |
| 15 | 17 Nov 2026 | Maven as the build tool | Already installed on the build machine; no reason to add another | This session |
| 16 | 17 Nov 2026 | One repository with `backend/` and `web/` | One place for code and documents; a session sees everything | This session |
| 17 | 17 Nov 2026 | The project is built feature by feature: master plan, then per feature a design, a task plan and the implementation, each approved by the user | The whole build is done by Claude Code across sessions with no memory; the documents carry the context | This session |
| 18 | 17 Nov 2026 | The spec is kept in the repository as `docs/spec/spec-v1.5.md` and never edited | Every session can read it; proposed changes stay in `poc-plan.md` section 5 | This session |

## Not decided yet

- Exact versions of Spring Boot, PostgreSQL, Next.js and the LLM SDK. Fixed in F01 and F06.
- The LLM models for Stage 1 and Stage 2. Fixed in F06 and F07.
- Each feature's "Open questions" in `docs/master-plan.md`.
- The 14 proposed spec changes in `poc-plan.md` section 5 are proposals to the spec owner. The PoC already follows them.
