# Backend

Spring Boot 4.1.x / Kotlin API, package-by-feature under `com.crichere.backend` (`auth/`, `profile/`, `reference/`, `common/`). Skeleton only for now — no business logic yet.

See `E:\Documentation\Crichere\ARCHITECTURE.md` for the coding pattern/architecture this project follows (package-by-feature, RFC 7807 error handling, JUnit5+MockK+Testcontainers, Jackson, manual DTO mapping, springdoc-openapi).

## Running locally

Local-only for this phase — no Railway deployment yet.

```
docker compose up -d          # starts Postgres (crichere_dev) on localhost:5432
./gradlew bootRun --args="--spring.profiles.active=local"
./gradlew test                # runs the Testcontainers-backed test suite (needs Docker running)
```

Secrets (JWT signing key, Firebase service account, AWS credentials, etc.) go in an untracked `application-local.yml` or `${ENV_VAR}` references — never commit real values. See `crichere.*` blocks in `src/main/resources/application.yml` for the placeholder config namespaces later tasks fill in.
