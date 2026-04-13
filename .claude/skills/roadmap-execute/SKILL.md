---
name: roadmap-execute
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Autonomously execute all steps in a ROADMAP.md file using sub-agents. Reads the roadmap, then processes each unchecked step sequentially — creating a spec, implementing it, reviewing it, and committing — by delegating each step to a dedicated sub-agent. The orchestrator stays lean and tracks overall progress. Use this skill when the user has a ROADMAP.md and wants Claude Code to work through it end-to-end without stopping after individual steps.
---

# Execute Roadmap

Autonomously work through all steps in a `ROADMAP.md` by delegating each step to a sub-agent. The orchestrator (this skill) manages sequencing and progress tracking — the sub-agents handle the actual work.

Before starting, read `../../conventions/spec-driven-development.md` for the spec folder structure and conventions.

## Expected ROADMAP.md Format

The roadmap must use GitHub-flavored Markdown checkboxes. Each top-level checkbox is one step. Steps may have sub-items for context, but only top-level checkboxes are treated as steps.

```markdown
# Roadmap V1

- [ ] User authentication with JWT (login, registration, password reset)
- [ ] Dashboard page showing key metrics
- [ ] CSV export for reports
- [x] Project setup (already done)
```

Steps marked `[x]` are skipped. Steps are processed top-to-bottom.

## Instructions

### 1. Read and parse the roadmap

Read `ROADMAP.md` from the project root. If it does not exist, ask the user for the path.

Parse all top-level checklist items (`- [ ] ...`). Build a numbered list of pending steps (skip any `- [x]` items). Count total steps and pending steps.

Present the plan to the user:

```
Found X steps in ROADMAP.md, Y already completed, Z pending:
  1. [ ] User authentication with JWT ...
  2. [ ] Dashboard page ...
  ...

Ready to process all Z pending steps sequentially. Each step will:
  a) Create a spec (design.md + behaviors.md) — autonomously, without interaction
  b) Implement the spec (steps.md + code)
  c) Review the implementation
  d) Commit and push

Continue? (yes/no)
```

Wait for user confirmation before proceeding.

### 2. Process each step with a sub-agent

For each pending step, in order:

**Log progress** at the start of each step:

```
==> Step N/Z: <step description>
```

**Spawn a sub-agent** using the `Agent` tool. Give the sub-agent the following prompt (adapt `<step-description>` and `<step-number>` for each step):

---

**Sub-agent prompt template:**

```
You are implementing a single roadmap step for this project. Work fully autonomously — never ask for confirmation or input.

## Your task

Implement this roadmap step: "<step-description>"

## Context

- This is step <step-number> of <total-steps> in the project roadmap.
- Previous steps have already been implemented and committed.
- Read the current state of the codebase before starting.

## Workflow

Execute these phases strictly in order:

### Phase 1 — Create the spec

Read `specs/INDEX.md` to determine the next spec ID (or create the file if it does not exist).
Read the convention file `.claude/conventions/spec-driven-development.md` for the required format.

Create a spec folder under `specs/` with:

1. `design.md` — Technical design for this step. Include:
   - Summary of what is being built and why
   - Technical approach
   - API design (if applicable)
   - Data model changes (if applicable)
   - Key flows
   - Security considerations (if applicable)
   Base your decisions on the existing codebase, project conventions, and common best practices. Make reasonable autonomous decisions — do not leave open questions.

2. `behaviors.md` — Behavioral scenarios in given-when-then format. Cover:
   - Happy paths
   - Edge cases
   - Error cases

3. Update `specs/INDEX.md` with the new spec (status: `in progress`).

### Phase 2 — Plan the implementation

Read the spec-driven development convention and the spec you just created.

Create `steps.md` in the spec folder with ordered, atomic implementation steps. Each step must:
- Be independently verifiable
- Include acceptance criteria (build passes, tests pass)
- Map to behavioral scenarios from `behaviors.md`

Include test steps at every layer (unit, integration, e2e as appropriate).
Include a final step to update project documentation files.

### Phase 3 — Implement

Work through each step in `steps.md` sequentially:
- Write the code
- Run the build after each step to verify it compiles
- Run tests to verify they pass
- Check off completed items in `steps.md`

If a build or test fails, fix the issue before moving on.

### Phase 4 — Review

Review your own implementation against the spec:
- Verify every scenario in `behaviors.md` has a passing test
- Verify the build passes cleanly
- Verify documentation was updated

If you find gaps, fix them.

### Phase 5 — Commit

Stage all changes and create a commit:
```
git add -A
git commit -m "feat: implement <spec-name> — <one-line summary>"
```

Update `specs/INDEX.md` to set the spec status to `done`.
Create a separate commit for the status update:
```
git add specs/INDEX.md
git commit -m "docs: mark <spec-name> as done"
```

### Phase 6 — Report

End your work with a brief summary:
- What was implemented
- Number of tests added
- Any issues encountered and how they were resolved
- Any compromises or known limitations
```

---

**After the sub-agent returns**, read its result and:

1. **Update ROADMAP.md** — Change the step from `- [ ]` to `- [x]`
2. **Log the result**:
   ```
   ==> Step N/Z completed: <brief summary from sub-agent>
   ```
3. If the sub-agent reported unresolved issues, log them but continue to the next step.

### 3. Push all changes

After all steps are processed, push to remote:

```
git push
```

### 4. Final summary

Present a summary to the user:

```
==> Roadmap execution complete!

Completed: X/Z steps
Specs created: <list>
Commits: <count>

Issues encountered:
- <any issues or compromises, or "None">
```

## Behavioral Rules

- **Sequential execution only** — Each step depends on the previous one. Never run steps in parallel.
- **Fresh sub-agent per step** — Each step gets a new sub-agent with a clean context. This prevents context bloat and ensures each agent sees the latest code state.
- **Never skip a failing step** — If a sub-agent fails, log the failure and ask the user whether to continue with the next step or abort.
- **Autonomous spec creation** — Unlike interactive `/spec-create`, the sub-agent creates specs without user interaction. It makes reasonable decisions based on the roadmap description and existing codebase.
- **Respect existing specs** — If a roadmap step already has a corresponding spec in `specs/INDEX.md` (matching by name/description), reuse it instead of creating a duplicate.
- **Git hygiene** — Each roadmap step produces its own commit(s). Do not batch multiple steps into one commit.

## When NOT to use this skill

- If the roadmap steps require significant design decisions or user input — use `/spec-create` interactively instead.
- If specs already exist and just need implementation — use the `automated-spec-implementation-prompt.md` directly.
- If you need fine-grained control over each step — work through them manually with `/spec-implement`.
