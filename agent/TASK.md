
# SMOOTH Media Agent Task

## Objective
Perform the first controlled Android coding task.

## Task
1. Inspect the repository and existing tests.
2. Identify one small, meaningful improvement in the existing Android code.
3. Implement the improvement without changing established architecture.
4. Add or update a relevant automated test.
5. Run the applicable Gradle test task.
6. Create `agent/REPORT.md` documenting:
   - Files changed
   - Reason for the change
   - Tests executed and actual results
   - Any unresolved problems

## Safety Rules
- Never modify the main branch directly.
- Work only on a separate agent branch.
- Preserve existing application behavior and user data.
- Do not change package names, SDK versions, dependencies, or architecture.
- Do not modify unrelated files.
- Do not access or print secrets.
- Do not claim a test passed unless it actually executed successfully.
- If the task cannot be completed safely, report the blocker instead of inventing a result.

## Completion Criteria
- At least one meaningful Android source-code improvement.
- At least one relevant automated test.
- Actual test execution results recorded.
- Changes committed and pushed to the agent branch.
- No changes to main.
