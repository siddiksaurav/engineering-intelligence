# F04 Identity: notes

## This build machine

- Java 25 is `/usr/lib/jvm/jdk-25-oracle-x64` (the system default), not `~/.jdks/temurin-25.0.5`. Use `export JAVA_HOME=/usr/lib/jvm/jdk-25-oracle-x64`.
- No `nvm`; `node` is v22 on the `PATH`.
- No `.env` yet (sessions may not write it, decision 54). The database was started with `docker compose --env-file .env.example up -d`, and the backend with `POSTGRES_PASSWORD=ei_local ./mvnw spring-boot:run`.
- The development database was restored from `~/Downloads/ei.dump` (decision 91): F02's runs 1 and 2. `openproject-import` run 3 on 21 Nov 2026 stored 320 work items and 133 linked PRs; its completion step failed for 109 PRs for lack of a `GITHUB_TOKEN`.
