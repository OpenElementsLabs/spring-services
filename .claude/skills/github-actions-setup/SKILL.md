---
name: github-actions-setup
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Create, review, or modify GitHub Actions CI/CD workflows for Open Elements projects (Java library, Java backend, TypeScript frontend/library, or fullstack application). Use this skill whenever the user wants to add CI, set up `.github/workflows/`, configure `build.yml`, `docs.yml`, or `release-drafter.yml`, add a docs deployment to GitHub Pages, wire up release drafting, or audit an existing workflow against Open Elements conventions. Trigger this skill even if the user only mentions "CI", "pipeline", "workflow", "GitHub Pages preview", or "release notes" without explicitly saying "GitHub Actions".
---

# GitHub Actions CI/CD Setup

This skill defines the GitHub Actions workflow conventions for Open Elements projects and walks you through creating or reviewing them.

Every Open Elements project must have CI that builds and tests on every push and pull request to `main`. The exact workflow shape depends on whether the project is a Java library/backend, a TypeScript frontend/library, or a fullstack application.

## When to use this skill

Use this skill when the user:

- Sets up a new repository and needs CI
- Adds GitHub Actions to an existing project that has none
- Reviews or refactors workflows already under `.github/workflows/`
- Wants documentation deployment (MkDocs → GitHub Pages) wired up
- Wants automated release notes via Release Drafter

## Instructions

1. **Determine the project type.** Inspect the repository to decide which template applies:
   - Java library or Java backend → Java template
   - TypeScript frontend or TypeScript library → TypeScript template
   - Fullstack repo with separate `backend/` and `frontend/` subdirectories → Fullstack template

   If the project type is not obvious, ask the user.

2. **Check for existing workflows.** Read any files in `.github/workflows/` first. If workflows exist, review them against the conventions in this skill rather than overwriting blindly.

3. **Apply the general rules** (see below) to every workflow you create or modify.

4. **Create or update `build.yml`** using the template that matches the project type.

5. **Optionally add `docs.yml`** if the project uses MkDocs for documentation (see the `mkdocs-setup` skill or check for an `mkdocs.yml` at the repo root).

6. **Optionally add `release-drafter.yml`** if the project publishes releases. This also requires a `.github/release-drafter.yml` configuration file.

7. **Verify pinning.** Before finishing, check that every `uses:` line pins a specific major version (e.g., `actions/checkout@v6`), never `@latest` or a floating tag.

## General Rules

- Workflows live in `.github/workflows/`.
- Trigger on `push` and `pull_request` to `main`.
- Fail fast: run formatting/linting checks before compilation and tests.
- Pin action versions (e.g., `actions/checkout@v6`, not `actions/checkout@latest`). This is also a reproducible-builds requirement.
- Use caching where available (Maven, pnpm, pip) to speed up builds.

## Build Workflow (`build.yml`)

The build workflow is the core CI pipeline. Its structure depends on the project type.

### Java Library / Backend

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          java-version: "25"
          distribution: "temurin"
      - run: |
          ./mvnw spotless:check
          ./mvnw clean verify
```

Key points:
- Use the Maven Wrapper (`./mvnw`), not a globally installed Maven.
- Run formatting check (`spotless:check`) before `clean verify`.
- `verify` includes compilation, tests, and any configured plugins (SBOM, etc.).
- Adapt `java-version` to match the project's `.sdkmanrc`.

### TypeScript Frontend / Library

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: pnpm/action-setup@v4          # reads the pnpm version from packageManager
      - uses: actions/setup-node@v6
        with:
          node-version-file: ".nvmrc"        # reads the Node version from .nvmrc
          cache: "pnpm"
      - run: |
          pnpm install --frozen-lockfile
          pnpm run format:check
          pnpm run test
          pnpm run build
```

Key points:
- **Do not hardcode versions in the workflow.** `pnpm/action-setup` reads the exact pnpm version from `package.json` → `packageManager`, and `actions/setup-node` reads the Node version from `.nvmrc` via `node-version-file`. This requires the project to declare both (see the `typescript-best-practices` skill).
- Do **not** also pass a `version:` input to `pnpm/action-setup` — a mismatch with `packageManager` is an error.
- `pnpm/action-setup` **must** run before `actions/setup-node`, because `cache: "pnpm"` needs pnpm on the `PATH` to locate the store.
- Cache the pnpm store via the `actions/setup-node` `cache: "pnpm"` option.
- Use `--frozen-lockfile` so the lockfile is authoritative — the install fails on drift, which is required for reproducible builds.
- Run format check and tests before build.
- Do **not** run `nvm` in CI: it is not available on GitHub-hosted runners, and `setup-node` already installs Node.

### Fullstack Application

For fullstack projects with separate `backend/` and `frontend/` directories, run both in parallel and add a Docker verification step:

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          java-version: "25"
          distribution: "temurin"
      - run: |
          ./mvnw spotless:check
          ./mvnw clean verify

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v6
      - uses: pnpm/action-setup@v4
        with:
          package_json_file: "frontend/package.json"  # reads pnpm version from the frontend's packageManager
      - uses: actions/setup-node@v6
        with:
          node-version-file: "frontend/.nvmrc"          # reads the Node version from frontend/.nvmrc
          cache: "pnpm"
          cache-dependency-path: "frontend/pnpm-lock.yaml"
      - run: |
          pnpm install --frozen-lockfile
          pnpm run format:check
          pnpm run test
          pnpm run build

  docker:
    runs-on: ubuntu-latest
    needs: [backend, frontend]
    steps:
      - uses: actions/checkout@v6
      - name: Build backend Docker image
        run: docker build -t app-backend ./backend
      - name: Build frontend Docker image
        run: docker build -t app-frontend ./frontend
      - name: Test Docker Compose
        run: docker compose build
```

Key points:
- Use `defaults.run.working-directory` for monorepo sub-directories. Note this affects only `run:` steps, **not** `uses:` steps — so action inputs that take a path (`node-version-file`, `package_json_file`, `cache-dependency-path`) must be given **relative to the repo root** (e.g. `frontend/.nvmrc`).
- As in the single-project template, versions are read from files rather than hardcoded: `package_json_file` points `pnpm/action-setup` at the frontend's `packageManager`, and `node-version-file` reads the frontend's `.nvmrc`.
- Backend and frontend jobs run in parallel.
- Docker job runs after both succeed (`needs: [backend, frontend]`).
- Set `cache-dependency-path` when the lockfile is not in the repo root.

## Documentation Workflow (`docs.yml`)

For projects using MkDocs with the Material theme:

```yaml
name: Docs

on:
  push:
    branches: [main]
    paths:
      - "docs/**"
      - "mkdocs.yml"
      - ".github/workflows/docs.yml"
  pull_request:
    branches: [main]
    paths:
      - "docs/**"
      - "mkdocs.yml"
      - ".github/workflows/docs.yml"

permissions:
  contents: write
  pull-requests: write

concurrency:
  group: "pages-${{ github.ref }}"
  cancel-in-progress: true

jobs:
  deploy-production:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: Configure Git Credentials
        run: |
          git config user.name github-actions[bot]
          git config user.email 41898282+github-actions[bot]@users.noreply.github.com
      - uses: actions/setup-python@v6
        with:
          python-version: "3.x"
      - uses: actions/cache@v5
        with:
          key: mkdocs-material-${{ hashFiles('mkdocs.yml') }}
          path: ~/.cache
          restore-keys: mkdocs-material-
      - run: pip install mkdocs-material "pymdown-extensions"
      - run: mkdocs gh-deploy --force

  deploy-preview:
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: Configure Git Credentials
        run: |
          git config user.name github-actions[bot]
          git config user.email 41898282+github-actions[bot]@users.noreply.github.com
      - uses: actions/setup-python@v6
        with:
          python-version: "3.x"
      - uses: actions/cache@v5
        with:
          key: mkdocs-material-${{ hashFiles('mkdocs.yml') }}
          path: ~/.cache
          restore-keys: mkdocs-material-
      - run: pip install mkdocs-material "pymdown-extensions"
      - run: mkdocs build --strict
      - uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./site
          destination_dir: "pr/${{ github.event.pull_request.number }}"
          keep_files: true
      - name: Comment PR with preview URL
        uses: actions/github-script@v8
        with:
          script: |
            const owner = context.repo.owner;
            const repo = context.repo.repo;
            const prNumber = context.issue.number;
            const url = `https://${owner}.github.io/${repo}/pr/${prNumber}/`;
            github.rest.issues.createComment({
              issue_number: prNumber, owner, repo,
              body: `Docs preview for this PR: ${url}`
            });
```

Key points:
- Only trigger on changes to `docs/`, `mkdocs.yml`, or the workflow itself (`paths` filter).
- Use `concurrency` to cancel outdated deployments.
- Production deploys on push to main, PR previews on pull requests.
- PR previews go to `/pr/<number>/` subdirectory and post a comment with the URL.

## Release Drafter (`release-drafter.yml`)

Automatically maintains a draft release based on merged PRs:

```yaml
name: Release Drafter

on:
  push:
    branches: [main]
  pull_request:
    types: [closed]

jobs:
  update_release_draft:
    if: github.event_name != 'pull_request' || github.event.pull_request.merged == true
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: read
    steps:
      - uses: release-drafter/release-drafter@v6
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

This requires a `.github/release-drafter.yml` configuration file in the repository. See the [release-drafter documentation](https://github.com/release-drafter/release-drafter) for template configuration.
