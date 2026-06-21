# Tool & Runtime Versions

This document defines how Node.js and pnpm versions are declared and kept in sync across local
development, CI, and the published package. The goal is a **single source of truth** for each
concern, so versions never drift between a developer's machine, the CI runner, and what consumers
of the published package see.

## Principles

- One source of truth per concern — never hardcode the same version in two places that can drift.
- Distinguish *compatibility* (what the package supports) from *pinning* (what we build with).
- CI reads the same files developers use locally — no separately maintained version numbers in
  workflows.
- Pin exact tool versions for reproducible builds; declare ranges only for consumer-facing
  compatibility.

## Overview: which file owns which version

| Concern              | File / field                       | Value type            | Who reads it                                              |
|----------------------|------------------------------------|-----------------------|-----------------------------------------------------------|
| Node — dev/CI pin    | `.nvmrc`                           | exact / major version | `nvm`/`fnm` locally, `actions/setup-node` (`node-version-file`) |
| Node — compatibility | `package.json` → `engines.node`    | range                 | npm/pnpm install checks, **consumers** of the package     |
| pnpm — exact version | `package.json` → `packageManager`  | exact version         | Corepack locally, `pnpm/action-setup` in CI               |
| Registry / auth      | `.npmrc`                           | —                     | npm/pnpm for publish & install                            |

Key distinction:

- **`.nvmrc`** answers *"which Node do we develop and build with?"* → a pin.
- **`engines.node`** answers *"which Node versions does this library run on?"* → a range, and it is
  published to the npm registry so consumers see it.
- `nvm` **does not** read `package.json`; `engines.node` is never used for local version switching.

## Node.js

### `.nvmrc` — the development & CI pin

```
24
```

- Contains the single Node version used for development and CI.
- `nvm use` / `fnm use` read it automatically.
- CI consumes it via `node-version-file` (see below), so the workflow never hardcodes a number.

### `package.json` → `engines.node` — the compatibility range

```json
"engines": {
  "node": ">=20"
}
```

- Declares the range of Node versions the published package supports.
- For a **library**, use a lower-bound range (e.g. `>=20`), not a single pinned version — pinning
  would force every consumer onto that exact major.
- Published to npm; tooling warns (or fails with `engine-strict=true`) when installed under an
  unsupported Node version.

> Note: `actions/setup-node` can read the version from `package.json` too
> (`node-version-file: "package.json"`, using `engines.node`). We deliberately keep `.nvmrc` as the
> pin so local `nvm` users get automatic version switching — `nvm` cannot read `package.json`.

## pnpm

### `package.json` → `packageManager` — the exact pnpm version

```json
"packageManager": "pnpm@11.3.0"
```

- The single source of truth for the pnpm version.
- Corepack uses it locally (`corepack enable`, then pnpm auto-resolves the pinned version).
- `pnpm/action-setup` reads it in CI when no `version` input is given — so the workflow does not pin
  pnpm separately.
- Do **not** also pass `version:` to `pnpm/action-setup`; a mismatch with `packageManager` is an
  error.

## `.npmrc` — registry & auth

```
//registry.npmjs.org/:_authToken=${NPM_TOKEN}
```

- Configures registry authentication via the `NPM_TOKEN` environment variable.
- Never commit an actual token — only the `${NPM_TOKEN}` reference. The token is supplied by CI
  secrets or the local environment.

## CI workflow

Because every version lives in a file, the workflow only references those files — it never repeats a
version number:

```yaml
    steps:
      - uses: actions/checkout@v4

      - uses: pnpm/action-setup@v4          # reads pnpm version from packageManager

      - uses: actions/setup-node@v4
        with:
          node-version-file: ".nvmrc"        # reads Node version from .nvmrc
          cache: "pnpm"

      - run: pnpm install --frozen-lockfile
      - run: pnpm run typecheck
      - run: pnpm run lint
      - run: pnpm run test
      - run: pnpm run build
```

Key points:

- `pnpm/action-setup` **must** run before `actions/setup-node`, because `cache: "pnpm"` needs pnpm
  on the `PATH` to locate the store.
- No version is hardcoded in the workflow — pnpm comes from `packageManager`, Node from `.nvmrc`.
- Do **not** run `nvm install` in CI: `nvm` is not available on GitHub-hosted runners, and
  `setup-node` already installs Node.
- `--frozen-lockfile` makes the lockfile authoritative (the install fails on drift), which is
  required for reproducible builds.

## Local development

- Run `nvm use` (or `fnm use`) to switch to the Node version from `.nvmrc`.
- Run `corepack enable` once so the pnpm version from `packageManager` is used automatically.
- Install with `pnpm install`.

## Updating versions

- **Bump Node (dev/CI):** edit `.nvmrc`, then re-run `nvm use` / `nvm install`. CI follows
  automatically.
- **Change the supported Node range:** edit `engines.node`. This is a consumer-facing change — treat
  a raised lower bound as potentially breaking and reflect it in the release notes / semver.
- **Bump pnpm:** edit `packageManager` (e.g. `pnpm@11.4.0`). Corepack and CI follow automatically.
  Commit the updated lockfile if dependency resolution changes.

## Do / Don't

- ✅ Keep each version in exactly one authoritative place.
- ✅ Use `node-version-file` and `packageManager` so CI mirrors the local setup.
- ✅ Use a range for `engines.node`, an exact version for `.nvmrc` and `packageManager`.
- ❌ Don't hardcode Node or pnpm versions inside the workflow.
- ❌ Don't run `nvm` in CI.
- ❌ Don't pin `engines.node` to a single exact version for a published library.
- ❌ Don't commit npm tokens — reference `${NPM_TOKEN}` only.
