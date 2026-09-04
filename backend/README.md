# Backend

Spring Boot 4.1.x / Kotlin API, package-by-feature under `com.crichere.backend` (`auth/`, `profile/`, `reference/`, `common/`). Phase 1 (phone-OTP auth, profile CRUD, S3 photo upload, states/cities reference) is implemented and tested.

See `E:\Documentation\Crichere\ARCHITECTURE.md` for the coding pattern/architecture this project follows (package-by-feature, RFC 7807 error handling, JUnit5+MockK+Testcontainers, Jackson, manual DTO mapping, springdoc-openapi).

## Running locally

Local-only for this phase — no Railway deployment yet.

```
docker compose up -d          # starts Postgres (crichere_dev) on localhost:55432
./gradlew bootRun --args="--spring.profiles.active=local"
./gradlew test                # runs the Testcontainers-backed test suite (needs Docker running)
```

Host port **55432**, not Postgres's default 5432 — some dev machines run a native Postgres service that's already bound to 5432 and silently intercepts connections meant for this container (hit during manual verification; symptom was a Flyway/Hikari `password authentication failed` error even though the container's own credentials were correct). If you change the port here, update `spring.datasource.url` in `src/main/resources/application.yml`'s `local` profile to match.

Secrets (JWT signing key, Firebase service account, AWS credentials, etc.) go in an untracked `application-local.yml` or `${ENV_VAR}` references — never commit real values. See `crichere.*` blocks in `src/main/resources/application.yml` for the placeholder config namespaces later tasks fill in.
