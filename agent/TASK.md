
# SMOOTH Media Agent Task

## Objective
Verify that the autonomous coding agent can inspect the repository,
create a file, run a test, and report its results.

## Task
1. Inspect the repository structure.
2. Read the root Gradle configuration.
3. Create `agent/REPORT.md`.
4. Write a short report containing:
   - Project structure
   - Android build configuration
   - Available Gradle test tasks
   - Any detected risks
5. Run `./gradlew help` if the environment supports it.
6. Record the command result in the report.

## Safety Rules
- Do not modify Android application source code.
- Do not modify existing Gradle files.
- Do not modify the main branch.
- Do not access or print secrets.
- Do not install unnecessary dependencies.
- Commit changes only to a separate agent branch.

## Completion Criteria
- `agent/REPORT.md` exists.
- The report contains actual repository findings.
- Command execution results are documented.
- All changes are committed to the agent branch.
