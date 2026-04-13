# Claude Code Base Configuration

This file provides base rules and conventions for Claude Code in Open Elements projects.
Projects that use this as a base can override or extend these rules in their own `CLAUDE.md`.

## Core Philosophy

- **Quality over speed.** Getting it right matters more than getting it done fast. Take the time needed for clean APIs,
  proper tests, correct architecture, and polished design.
- **Iterative improvement is expected.** Code and design will evolve through iterations. It is normal and encouraged
  that things change and improve as new features are added or understanding deepens. Do not over-optimize for a "final"
  state on the first pass.

## Code Quality

- Follow the DRY principle — avoid duplicating logic. Extract shared code into reusable functions or modules.
- Follow the KISS principle — prefer simple, readable solutions over clever or complex ones.
- Remove dead code. Do not leave commented-out code, unused imports, or unreachable branches.
- Keep functions and methods focused — each should do one thing well.
- Prefer meaningful names for variables, functions, and classes. Avoid abbreviations unless they are widely understood (
  e.g., `id`, `url`).
- Do not add code "for future use." Only implement what is currently needed.

## Security

- **IMPORTANT**: Never read or write files outside the project directory unless the user explicitly asks for it.
- **IMPORTANT**: Never modify system-level configuration files (shell profiles, system packages, etc.).
- **IMPORTANT**: Never commit, log, or echo secrets, API keys, passwords, or tokens. Use environment variables or secret
  management tools.
- **IMPORTANT**: Always include `.env` in `.gitignore` to prevent accidental commits of local configuration with
  secrets.
- Validate and sanitize all external input (user input, API responses, file contents).
- **IMPORTANT**: Use parameterized queries for database access — never build SQL from string concatenation.
- Keep dependencies up to date to avoid known vulnerabilities.
- See [Security Configuration](.claude/conventions/security.md) for concrete `.claude/settings.json` deny rules, sandbox
  setup, and hook examples.

## Testing

- Write tests for new features and bug fixes.
- Tests should be deterministic — no flaky tests that depend on timing, network, or random state.
- Each test should test one behavior and have a clear name that describes what it verifies.
- Prefer assertion libraries that produce clear failure messages.

## Documentation

- Use GitHub Flavored Markdown (GFM) as the default syntax for all documentation (`README.md`, docs, ADRs, etc.).

## Pull Requests and Reviews

- Keep PRs focused on a single change. Avoid mixing unrelated changes in one PR.
- Write a clear PR description that explains what changed and why.
- Ensure all tests pass before requesting review.
- Address review comments before merging.

## Additional Conventions

**IMPORTANT**: Only include the documents that are relevant to your project. Do not reference all docs — each referenced
file is loaded into Claude's context and excessive context causes rules to be ignored. A Java library does not need
`typescript.md` or `fullstack-architecture.md`. A frontend does not need `java.md` or `backend.md`.

Typical combinations:

- **Java library**: `software-quality.md`, `java.md`, `repo-setup.md`
- **Java backend**: `software-quality.md`, `java.md`, `backend.md`, `repo-setup.md`
- **TypeScript frontend**: `software-quality.md`, `typescript.md`, `repo-setup.md`
- **Fullstack application**: `software-quality.md`, `java.md`, `typescript.md`, `backend.md`,
  `fullstack-architecture.md`, `repo-setup.md`

Available documents:

### Language-Specific

- [Java Conventions](.claude/conventions/java.md) — code style, build tools, testing, logging, null handling,
  collections, JPMS, SPI
- [TypeScript Conventions](.claude/conventions/typescript.md) — technology stack, code style, package manager, testing,
  linting

### Security

- [Security Configuration](.claude/conventions/security.md) — permission deny rules, sandbox mode, hooks for safety,
  audit logging

### Architecture and Infrastructure

- [Software Quality and Architecture](.claude/conventions/software-quality.md) — API design, technical integrity,
  namespace, SBOM, CI
- [Fullstack Architecture](.claude/conventions/fullstack-architecture.md) — frontend/backend separation, Docker,
  configuration, pinned tool versions
- [Backend Conventions](.claude/conventions/backend.md) — REST APIs, OpenAPI, Swagger UI

### Development Workflow

- [Spec-Driven Development](.claude/conventions/spec-driven-development.md) — specs folder structure, design docs,
  behavioral scenarios, implementation steps
- [Spec Index](specs/INDEX.md) — central index of all specifications with status, areas, and GitHub issue references.
  Read this file to discover which specs exist and their current state.
- [Roadmap](ROADMAP.md) — optional high-level project roadmap with checkboxes for each milestone. When present, use
  `/roadmap-execute` to autonomously work through all steps end-to-end (spec creation, implementation, review, commit).

### Build Integrity

- [Reproducible Builds](.claude/conventions/reproducible-builds.md) — version pinning, deterministic output, common
  pitfalls, build verification scripts

### CI/CD

- [GitHub Actions](.claude/conventions/github-actions.md) — build workflows, docs deployment, release drafter for Java,
  TypeScript, and fullstack projects

### Documentation and Repository Setup

- [Repository Setup](.claude/conventions/repo-setup.md) — required root files (README, LICENSE, CoC, CONTRIBUTING,
  .editorconfig)
- [EditorConfig](.claude/conventions/editorconfig.md) — standard .editorconfig for Java and TypeScript projects
- [Project Documentation](.claude/conventions/documentation.md) — Markdown, MkDocs setup, GitHub Pages deployment, ADRs

### Project-Specific

- [Project-Specific Docs](.claude/conventions/project-specific/README.md) — project-specific conventions and
  documentation (add your own here)
