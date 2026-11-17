# Progress

One entry per working session, newest at the top. Each entry says what was done, what is next and what is blocked. Read the top entry first.

## 17 Nov 2026: F01 deep dive, design written

**Done**
- F01 deep dive held with the user. All open questions of the F01 block are answered.
- `docs/features/F01-repository-skeleton/design.md` written. State: draft, waiting for the user's approval.
- Decisions 19 to 27 added to `docs/decisions.md`.
- F01 set to "in design" in the master plan.

**Next**
- The user reads and approves the design. Then the task plan, `docs/features/F01-repository-skeleton/plan.md`.

**Blocked or needed from the user**
- Approval of the F01 design.
- JDK 25 is still not installed. It is the first task of the F01 implementation (Temurin 25 into `~/.jdks/`, approved by the user).
- A read-only GitHub token is needed from F02.
- An LLM API key is needed from F06.

## 17 Nov 2026: documentation set created

**Done**
- `poc-plan.md` updated: Java 25, Spring Boot 4.x, Maven.
- Spec copied to `docs/spec/spec-v1.5.md`.
- `docs/master-plan.md` written: 13 features (F01 to F13), global constraints, out of scope, status table, glossary.
- `CLAUDE.md`, `docs/features/README.md`, `docs/features/_template-design.md`, `docs/decisions.md` written.
- Git repository created. No application code exists.

**Next**
- F01 Repository skeleton: deep dive with the user, written to `docs/features/F01-repository-skeleton/design.md`.

**Blocked or needed from the user**
- JDK 25 is not installed on this machine (OpenJDK 21 only). F01 needs it. Installing needs the user's approval.
- A read-only GitHub token is needed from F02.
- An LLM API key is needed from F06.
