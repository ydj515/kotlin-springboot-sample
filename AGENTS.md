# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Kotlin Spring Boot API. Main code lives under `src/main/kotlin/com/example/kotlinspringbootsample` and follows layered packages:
- `presentation`: controllers plus `request` and `response` DTOs
- `application`: `*UseCase`, `command`, `result`, and mapping helpers
- `domain`: entities, policies, exceptions, and repository ports
- `infrastructure`: JWT/filter/bootstrap adapters
- `config`, `common`: Spring configuration and shared base types

Tests mirror the same structure under `src/test/kotlin`. Runtime examples live in `src/main/resources/application-sample.yml`; test profile config lives in `src/test/resources/application-test.yml`.

## Build, Test, and Development Commands
- `mise install`: install pinned `java 21.0.2` and `gradle 8.14.4`
- `mise run`: run `./gradlew bootRun`
- `mise run test`: run the full test suite
- `mise run build`: compile, test, and package the app
- `./gradlew bootRun`: start the API directly
- `./gradlew build`: full CI-equivalent build

## Coding Style & Naming Conventions
Use standard Kotlin style with 4-space indentation and UTF-8 files. Prefer `val` unless JPA or framework binding requires mutation. Keep controller DTOs in `presentation/*/{request,response}`, use `*UseCase` in `application`, and keep domain rules in `domain/*/policy`. Follow names like `PostUseCase`, `OrderUseCase`, `SignupCommand`, `OrderResult`, and `OrderRepositoryTest`.

## Testing Guidelines
Use Spring Boot Test, Kotest, MockK, and SpringMockK. Name test files with the `*Test.kt` suffix and keep them in the same layered package path as the code they verify. Prefer `@WebMvcTest` for presentation tests and `@DataJpaTest` for JPA mapping checks such as `OrderRepositoryTest`.

## Commit & Pull Request Guidelines
Recent history follows short prefixes such as `feat:`, `fix:`, `test:`, `build:`, and `ci:`. Keep commits imperative and focused. PRs should include a summary, test results, and notes for config or schema-impacting changes. For API changes, include request/response examples.

## Security & Configuration Tips
Do not commit real secrets. Keep local-only overrides in ignored files such as `application.yml`, and use `application-sample.yml` as the shared template. JWT values required for tests are intentionally provided by `application-test.yml`.
