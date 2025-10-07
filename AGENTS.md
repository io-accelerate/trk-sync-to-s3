# Repository Guidelines

## Project Structure & Module Organization
This project is a multi-module Gradle build. The core library lives in `sync-to-s3`, whose production code resides under `sync-to-s3/src/main/java` and tests under `sync-to-s3/src/test/java` with supporting fixtures in `src/test/resources`. The command-line facade is packaged in `sync-to-s3-cli`, producing a shaded executable JAR in `sync-to-s3-cli/build/libs`. Shared Gradle scripts sit in `build-logic`, while helper shell scripts in the repository root support credential rotation and release packaging.

## Build, Test & Development Commands
Use `./gradlew clean build` to compile every module and run the full test matrix. Execute `./gradlew :sync-to-s3:test` for library-only checks, or `./gradlew :sync-to-s3-cli:shadowJar -x test` when iterating on the CLI binary. Install snapshot artifacts locally with `./gradlew publishToMavenLocal`, then generate a Sonatype upload bundle via `./generateMavenCentralBundle.sh` before pushing to staging.

## Coding Style & Naming Conventions
Java sources follow four-space indentation with braces on the same line as declarations. Keep packages rooted at `io.accelerate.tracking.sync`. Classes adopt UpperCamelCase (`RemoteSync`), methods and variables use lowerCamelCase, and constants stay SCREAMING_SNAKE_CASE. Mirror existing patterns that favour explicit builders and listener interfaces; avoid static utility shortcuts unless they already exist. No formatter runs automatically, so apply IntelliJ’s default Java style and double-check imports.

## Testing Guidelines
The suite relies on JUnit 5 and Hamcrest. Name end-to-end checks with the `_AcceptanceTest` suffix (e.g., `FileUpload_AcceptanceTest`) and place concurrency or progress-related tests alongside the existing `sync/progress` package. Keep new fixtures in the module-specific `src/test/resources` tree. Run focused commands such as `./gradlew :sync-to-s3:test --tests "*AcceptanceTest"` during development, and execute `./gradlew clean build` before publishing a branch to ensure CLI packaging still succeeds.

## Commit & Pull Request Guidelines
Commits follow Conventional Commit prefixes (`feat:`, `fix:`, `chore:`) as reflected in recent history. Group related code, resource, and configuration changes together so reviewers can run a single build. Pull requests should include a short problem statement, the solution outline, validation steps (`./gradlew clean build`, CLI smoke tests), and links to tracking issues. Attach CLI output or screenshots whenever behaviour visible to operators changes.

## Security & Configuration Tips
Do not commit secrets; instead, store test credentials in `.private/aws-test-secrets` using the documented property keys. Refresh temporary AWS credentials with `./use_temp_creds.sh <CONFIG_FILE> sts get-caller-identity` before running sync commands. Clean up local S3 experiments by listing and aborting multipart uploads with the AWS CLI examples provided in `README.md` to avoid lingering charges.
