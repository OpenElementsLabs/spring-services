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
- Concrete `.claude/settings.json` deny rules, sandbox setup, and hook examples live in the
  `agentic-support-workflow` plugin (`conventions/security.md`).

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

The Open Elements conventions are no longer copied into `.claude/conventions/`. They ship as Claude Code plugins and are
loaded automatically when a task matches a skill's description — nothing has to be referenced from this file for them to
apply, and there is no context cost for skills that stay unused. Which plugins are enabled is recorded in
[`.claude/settings.json`](.claude/settings.json).

### Relevant for this project (Java library / Spring Boot starter)

| Skill | Covers |
| --- | --- |
| `agentic-support-open-elements:java-best-practices` | Code style, build tools, testing idioms, logging, JSpecify null handling, collections, immutability, JPMS, SPI |
| `agentic-support-open-elements:java-backend` | Spring Boot conventions, feature-based packages, REST/OpenAPI, JPA/Flyway/PostgreSQL, layer-specific testing |
| `agentic-support-coding:java-api-design` | Public API surface, hiding implementations, API evolution and breaking changes, module boundaries |
| `agentic-support-coding:modern-java` | Modern Java idioms (records, sealed types, pattern matching, text blocks) instead of legacy patterns |
| `agentic-support-open-elements:project-setup` | Project baseline: project type, required root files, `.editorconfig` |
| `agentic-support-open-elements:github-actions-setup` | CI/CD workflows (`build.yml`, `docs.yml`, `release-drafter.yml`) |
| `agentic-support-open-elements:mkdocs-setup` | Project documentation with MkDocs + Material, published to GitHub Pages |

Software quality and architecture rules (API design, technical integrity, namespace, SBOM, CI coverage thresholds) are
bundled with those plugins as `conventions/software-quality.md` and referenced by the skills above — do not copy that
document into this repository.

The TypeScript, frontend and fullstack-architecture skills exist but are not relevant here: this repository is a Java
library with no frontend and no deployable application.

### Development Workflow

- **Spec-driven development** — every specification lives in its own folder under `docs/specs/` (`design.md` plus
  `behaviors.md`). The `agentic-support-workflow` plugin provides the workflow skills: `spec-create`, `spec-implement`,
  `spec-review` and the end-to-end `spec-flow`.
- [Spec Index](docs/specs/INDEX.md) — central index of all specifications with status, areas, and GitHub issue
  references. Read this file to discover which specs exist and their current state.
- [Open TODOs](docs/TODO.md) — work deliberately deferred out of a spec, with the context needed to pick it up later.
- **Reproducible builds** — version pinning, deterministic output and build verification are checked by the
  `agentic-support-workflow:reproducible-builds-check` skill. The build timestamp is fixed centrally via
  `project.build.outputTimestamp` in the `java-parent` POM.
