# CLAUDE.md

## 1. Project Instructions

You are working as an AI coding agent on this project.

Before making any changes:

* Understand the existing project structure.
* Inspect the relevant files before modifying them.
* Follow the existing architecture and coding style.
* Do not rewrite or refactor unrelated code.
* Do not make assumptions about requirements when the existing code provides the answer.
* Keep changes focused on the requested task.

---

# 2. Task Execution Rules

For every implementation task, follow this workflow:

```text
Understand Task
      ↓
Inspect Existing Code
      ↓
Check Git Status
      ↓
Create Task Branch
      ↓
Implement
      ↓
Test
      ↓
Review Changes
      ↓
Commit
      ↓
Push
      ↓
Create Pull Request
      ↓
Wait for Review
```

Do not skip important steps.

---

# 3. Git Safety Rules

## 3.1 Never Work Directly on Main

Never implement a task directly on:

```text
main
```

Every implementation task must have its own branch.

Use:

```text
feature/<task-name>
```

For bug fixes:

```text
fix/<bug-name>
```

For documentation:

```text
docs/<topic-name>
```

Examples:

```text
feature/spotify-play-pause
feature/spotify-search
fix/spotify-popup-handling
docs/automation-testing
```

---

# 4. Check Git Status First

Before changing anything, run:

```bash
git status
```

If the working tree contains changes that were already present before your work:

STOP.

Do not automatically:

```bash
git add .
git commit
git stash
git reset
```

Instead, report the existing changes to me and determine which changes belong to the current task.

Never overwrite or commit another person's/local work without permission.

---

# 5. Start From Latest Main

Before starting a new implementation task:

```bash
git checkout main
git pull origin main
```

Then create the task branch:

```bash
git checkout -b feature/<task-name>
```

If the repository uses another default branch, inspect the repository and use the actual default branch instead of assuming `main`.

---

# 6. One Task = One Branch

Each independent implementation task must have its own branch.

Example:

```text
main
 │
 ├── feature/spotify-play-pause
 │
 ├── feature/spotify-next-previous
 │
 ├── feature/spotify-search
 │
 └── fix/spotify-popup
```

Never combine unrelated tasks into one branch.

If I explicitly ask you to implement multiple tightly related changes as one task, they may belong to the same branch.

---

# 7. Inspect Before Coding

Before implementing a task:

1. Identify the relevant files.
2. Understand the existing implementation.
3. Check related components/classes/services.
4. Check existing error handling.
5. Check existing tests.
6. Check how the feature is currently connected to the rest of the application.

Do not immediately start writing code without understanding the existing implementation.

Prefer modifying existing architecture over creating duplicate functionality.

---

# 8. Implementation Rules

When implementing a task:

* Implement only what is required.
* Follow existing naming conventions.
* Follow existing folder structure.
* Reuse existing utilities/components/services when appropriate.
* Avoid unnecessary dependencies.
* Avoid unnecessary refactoring.
* Avoid changing APIs/interfaces unless required.
* Avoid breaking existing functionality.
* Keep the implementation maintainable and readable.

Do not introduce unrelated improvements just because you notice them.

If an unrelated issue is discovered, report it instead of silently fixing it.

---

# 9. Dependencies

Do not install new dependencies unless they are actually required.

Before installing a dependency:

1. Check whether the project already has an equivalent dependency.
2. Check whether the functionality can be implemented using existing dependencies.
3. Install only when justified by the task.

Do not unnecessarily upgrade existing packages.

---

# 10. Environment and Secrets

Never commit:

```text
.env
.env.*
```

or:

* API keys
* passwords
* access tokens
* private credentials
* certificates
* private configuration
* personal secrets

If a secret is accidentally detected:

STOP and report it.

Do not expose secrets in:

* commits
* PR descriptions
* logs
* screenshots
* test output

---

# 11. Testing Requirements

After implementation, test the actual functionality.

Depending on the project, use appropriate commands such as:

```bash
npm test
npm run test
npm run build
npm run lint
```

Do not blindly run commands that do not exist in the project.

First inspect:

```bash
package.json
```

and determine the project's actual scripts.

For Android/Java components, use the project's existing build/test commands.

For automation features, perform actual execution tests where possible.

---

# 12. Test Results Must Be Honest

Never claim:

```text
Tests passed
```

unless the tests were actually executed.

Never invent:

* screenshots
* logs
* test results
* execution evidence
* API responses
* device results
* performance results

If something could not be tested, explicitly state:

```text
Not tested because: <reason>
```

---

# 13. Functional Validation

For every implementation task, verify:

### Happy Path

The feature works as expected under normal conditions.

### Failure Path

The feature behaves correctly when something goes wrong.

### Edge Cases

Check relevant cases such as:

* missing data
* invalid input
* unavailable UI elements
* network interruption
* timeout
* application interruption
* repeated execution
* unexpected state

Only test cases relevant to the task.

---

# 14. Review Changes Before Commit

Before committing:

```bash
git status
```

Then:

```bash
git diff
```

Review every changed file.

Make sure:

* Changes belong to the current task.
* No unrelated files were modified.
* No debugging code remains.
* No temporary files are included.
* No secrets are included.
* No generated files are accidentally included.
* No accidental formatting changes are present.

If unrelated changes are detected:

STOP and report them.

---

# 15. Commit Rules

Create focused commits.

Use conventional-style commit messages where appropriate:

```text
feat: implement Spotify play pause automation
fix: handle Spotify popup during automation
test: add automation validation tests
docs: update automation documentation
refactor: simplify Spotify command handling
```

Do not use vague messages such as:

```text
changes
update
work
fixed stuff
final
test
abc
```

---

# 16. Push Rules

After successful testing and commit:

```bash
git push -u origin <branch-name>
```

Verify that the branch was successfully pushed.

---

# 17. Pull Request Rules

After pushing the task branch, create a Pull Request:

```text
<task-branch> → main
```

Do NOT merge the Pull Request automatically.

The Pull Request must remain available for review.

Only merge when I explicitly instruct you to merge it.

---

# 18. Pull Request Title

The PR title must be:

* Clear
* Concise
* Specific
* Related to the actual implementation

Examples:

```text
Implement Spotify Play/Pause Automation
Add Spotify Next and Previous Track Controls
Handle Spotify Popups During Automation
Add End-to-End Spotify Command Validation
```

Avoid vague titles:

```text
Update code
Changes
Task done
Final implementation
```

---

# 19. Pull Request Description

Every PR must use this structure:

```markdown
## Overview

Briefly explain what this PR implements.

## Problem

Explain the requirement or problem addressed by this task.

## Solution

Explain the technical approach used.

Mention relevant:

- components
- classes
- services
- APIs
- logic
- architecture

## Changes Made

- Change 1
- Change 2
- Change 3

## Testing

Describe the tests that were actually performed.

| Test Case | Expected Result | Actual Result | Status |
|------------|-----------------|---------------|--------|
| Test 1 | Expected behavior | Observed behavior | PASS |
| Test 2 | Expected behavior | Observed behavior | PASS |

## Error Handling / Edge Cases

Describe relevant failure scenarios and how they are handled.

## Evidence

Include or reference actual:

- screenshots
- logs
- recordings
- test output

Do not invent evidence.

## Files Changed

Briefly explain the important files modified.

## Notes

Mention any limitations, assumptions, or decisions that reviewers should know about.
```

---

# 20. PR Description Must Match Reality

The PR description must describe what was actually implemented.

Do not exaggerate.

For example, if only Play/Pause was implemented, do not write:

```text
Implemented complete Spotify automation.
```

Instead write:

```text
Implemented Spotify Play/Pause automation.
```

If testing was performed only on an Android emulator, say:

```text
Tested on Android emulator.
```

Do not claim physical-device testing unless it actually happened.

---

# 21. PR Review Preparation

Before creating the PR, ask yourself:

```text
Does this PR contain only one logical task?

Does the code build?

Did I test the implementation?

Did I review git diff?

Are there unrelated changes?

Are there secrets?

Does the PR description accurately describe the implementation?

Can a senior developer understand what changed and why?
```

If the answer to any important question is NO, fix the issue before creating the PR.

---

# 22. Do Not Merge Automatically

Never execute:

```bash
git merge
```

or merge/close the Pull Request automatically unless I explicitly request it.

The purpose of the Pull Request is to allow review.

The expected workflow is:

```text
Developer
   ↓
Creates PR
   ↓
Senior Reviews
   ↓
Changes Requested
   ↓
Developer Updates Same Branch
   ↓
PR Automatically Updates
   ↓
Senior Approves
   ↓
PR Merged
```

---

# 23. Handling Review Comments

If the senior requests changes:

1. Read all review comments.
2. Understand what needs to change.
3. Modify the SAME task branch.
4. Test the changes.
5. Commit the changes.
6. Push the same branch.

Do NOT create a second PR for the same review cycle unless explicitly requested.

The existing PR will update automatically.

---

# 24. Final Report After PR Creation

After creating a PR, report the following:

```text
Task:
<task name>

Branch:
<branch name>

Commit:
<commit hash>

PR:
<PR number>

PR Title:
<title>

PR URL:
<url>

Implementation:
<short summary>

Testing:
<tests performed>

Result:
<pass/fail/partial>

Evidence:
<available evidence>

Notes:
<any limitations or decisions>
```

Never invent a PR number, URL, commit hash, test result, or evidence.

---

# 25. Important Git Rules

NEVER:

```text
force push without permission
reset another person's work
delete another person's branch
commit secrets
commit .env files
merge unrelated changes
work directly on main
```

Be especially careful with destructive commands:

```bash
git reset --hard
git clean -fd
git push --force
git branch -D
```

Do not use them unless explicitly authorized and you fully understand their impact.

---

# 26. When Requirements Are Ambiguous

If the requested task is unclear:

* Inspect the existing code first.
* Determine what can be safely inferred.
* If an important implementation decision cannot be determined, ask before making a potentially breaking change.

Do not invent requirements.

---

# 27. Completion Criteria

A task is considered complete only when:

```text
[ ] Task understood
[ ] Relevant code inspected
[ ] Git status checked
[ ] Correct task branch created
[ ] Implementation completed
[ ] Happy path tested
[ ] Relevant failure cases tested
[ ] Build/test checks completed
[ ] Git diff reviewed
[ ] No unrelated changes
[ ] No secrets
[ ] Commit created
[ ] Branch pushed
[ ] Pull Request created
[ ] PR title written
[ ] PR description written
[ ] Testing documented
[ ] Evidence documented when available
[ ] PR NOT merged automatically
```

---

# 28. Core Principle

Always prioritize:

```text
Correctness
   >
Safety
   >
Task Scope
   >
Testability
   >
Code Quality
   >
Speed
```

Do not sacrifice correctness or repository safety merely to finish a task faster.

For every implementation task, leave the repository in a clean, reviewable state and create a focused Pull Request that allows a senior developer to understand exactly what was changed, why it was changed, and how it was tested.
