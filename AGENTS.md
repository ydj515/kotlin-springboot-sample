# Repository Guidelines

## Project Structure & Module Organization
This is a Kotlin Spring Boot API project with a single Gradle module. Application code lives under `src/main/kotlin/com/example/kotlinspringbootsample`, organized by feature (`auth`, `post`, `user`) and shared infrastructure (`config`, `common`, `filter`). Runtime configuration samples live in `src/main/resources` as `application-sample.yml` and `jwt-sample.yml`. Tests live under `src/test/kotlin` with matching package structure, and test-only secrets go in `src/test/resources`.

## Build, Test, and Development Commands
- `mise install`: install the pinned toolchain from `mise.toml` (`java 21.0.2`, `gradle 8.14.4`).
- `./gradlew test`: run the full test suite with JUnit 5 and Kotest.
- `./gradlew bootRun`: start the API locally.
- `./gradlew bootJar`: build the runnable JAR in `build/libs/`.
- `docker build -t kotlin-springboot-sample .`: build the production container image.
- `docker build -f Dockerfile-local -t kotlin-springboot-sample:local .`: build with the local Gradle-based Docker flow.

## Coding Style & Naming Conventions
Use standard Kotlin style with 4-space indentation and UTF-8 source files. Prefer `val` over `var` unless mutation is required by JPA or framework binding. Keep packages feature-oriented. Follow existing naming patterns such as `PostController`, `UserService`, `TokenProvider`, `PostRequest`, and `PostRepositoryTest`. No dedicated formatter or linter is configured, so use IntelliJ IDEA’s Kotlin formatter before committing.

## Testing Guidelines
Use Spring Boot Test, Kotest, MockK, and SpringMockK. Name test files with the `*Test.kt` suffix and keep them in the same package path as the code they verify. Favor focused slice tests such as `@WebMvcTest` and `@DataJpaTest` before adding full `@SpringBootTest` coverage. Run `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines
Recent history follows short prefix-based commits such as `feat:`, `fix:`, `test:`, `build:`, and `ci:`. Keep messages imperative and scoped, for example `fix: remove hard-coded identity id in data loader`. PRs should include a summary, linked issue if available, test results, and notes for any config, Docker, or CI changes. For API changes, include example request/response payloads.

## Security & Configuration Tips
Do not commit real secrets. Copy sample config values from `src/main/resources/*-sample.yml` into local-only files such as `application.yml` and `jwt.yml`. Keep JWT keys and environment-specific settings outside version control.
