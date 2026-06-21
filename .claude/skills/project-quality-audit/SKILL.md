---
name: project-quality-audit
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Audit the overall quality and release-readiness of an ENTIRE project (not a single change). Checks that the project has a complete README, a declared license, that it actually builds the way the README says and that an outside consumer could resolve its dependencies, that public API documentation (Javadoc/TSDoc) exists and does not contradict the code, that test coverage meets the Open Elements thresholds (backend ≥80%, frontend ≥70%), and that no secrets are committed and nothing in the repo would embarrass a public release. Use this skill whenever the user wants to know if a project or repository is healthy, well-documented, release-ready, or up to standard — for phrasings like "audit this project", "is my repo ready to release", "check the overall quality", "does this project meet our standards", "review the whole project", or before a first public release. This is a read-only, whole-project audit; for reviewing the quality of a specific code change or diff, use the `quality-review` skill instead.
---

# Project Quality Audit

Assess the overall health and release-readiness of a whole project and produce a structured, read-only report. This is a project-level audit — it answers "is this project in good shape?", not "is this change good?". For change/diff-level review, that is what the `quality-review` skill is for; the two are complementary.

The audit is **read-only**: investigate, build, test, and report — but do not modify the project. If the user wants fixes afterwards, that is a separate, explicit follow-up. A neutral audit is only trustworthy if running it never changes the thing being measured.

## How to work through this

Run the audit in three phases: **(1) understand the project**, **(2) run each check**, **(3) write the report**. Each check below states what to verify, how to verify it, and what counts as pass / warning / fail. Investigate every check unless it genuinely does not apply (e.g., no public API in an internal-only app) — and when it doesn't apply, say so explicitly rather than silently skipping it. Silent gaps make an audit untrustworthy.

### Phase 1 — Understand the project (the "build contract")

Before checking anything, learn what the project claims to be:

- Detect the project type and toolchain: Java (Maven `pom.xml` / Gradle), TypeScript/JS (`package.json` + pnpm/npm/yarn), or a fullstack project with both (e.g., a `backend/` and `frontend/`). A fullstack project is audited as two parts with their respective thresholds.
- Read `README.md` end to end. Treat it as the **contract**: the build/run/test commands it documents are exactly what you will execute later. The README is the source of truth for *how a newcomer is told to build the project* — the audit verifies reality against that promise.
- Note the declared toolchain versions (`.sdkmanrc`, `.nvmrc`, `pom.xml`, `package.json`) so you build with what the project expects. If a pinned version is unavailable, note it as a finding rather than silently building with a different one.

### Phase 2 — Run the checks

## Check 1 — README completeness

A good README lets a newcomer understand and run the project without asking anyone. Verify it exists at the repo root and covers:

- **What the project is** — purpose and scope in the first paragraphs.
- **Prerequisites** — required tools and versions (JDK, Node, Docker, etc.).
- **Build instructions** — the exact command(s) to build.
- **Run instructions** — how to start or use the project.
- **Test instructions** — how to run the tests.
- **License reference** — names the license and points to the `LICENSE` file.
- **Contribution pointer** — links to `CONTRIBUTING.md` / `CODE_OF_CONDUCT.md` if the project accepts contributions.

Pass: all essential sections present and accurate. Warning: present but thin or partly outdated. Fail: missing, or missing build/run instructions (because then Check 3 cannot be trusted against a documented contract).

## Check 2 — License

Open source projects must state their license unambiguously, and consumers/tools (SBOM, package registries) read it from build metadata.

- A `LICENSE` (or `LICENSE.txt`) file exists at the repo root.
- The license is also declared in build metadata: `pom.xml` `<licenses>` for Maven, `license` field in `package.json` for Node. These must match the `LICENSE` file.
- Build metadata more broadly (name, description, URL, developer/organization info) is present — this is required for generated artifacts and SBOMs.

Pass: license file present and consistently declared in metadata. Warning: file present but missing/mismatched in metadata. Fail: no license at all.

## Check 3 — Buildable as documented

"It builds on my machine" is not enough; it must build the way the README tells a newcomer to build it. This is why Check 1 gates this one.

- Run the build command(s) from the README (prefer the wrapper: `./mvnw clean verify`, `pnpm install && pnpm build`, etc.).
- Capture the outcome. A clean success is a pass. A failure is a fail — include the relevant error output in the report, not just "build failed".
- If the README documents no build command, report that under Check 1 and attempt the conventional command for the detected toolchain, clearly noting that you inferred it.

See the `reproducible-builds-check` skill for a deeper look at build determinism and version pinning — this check only verifies that the documented build succeeds, not that it is bit-for-bit reproducible.

## Check 4 — Dependencies resolvable by an outside consumer

"It builds for me" can be a false positive: your local machine may have private/internal dependencies cached, or be authenticated to a private registry that the public is not. For a project that is about to be released, the real question is whether *someone who just cloned it* can resolve every dependency. This is why a green build on the author's machine is necessary but not sufficient.

- Inspect declared dependencies and repositories: Maven `<repositories>`/`<dependencies>` and `settings.xml`; `package.json` dependencies plus any `.npmrc`/registry overrides.
- Confirm that every dependency comes from a **public** source (Maven Central, the public npm registry, or another openly reachable repository). Pay special attention to in-house Open Elements artifacts (e.g. `com.open-elements:*`, `@open-elements/*`) — verify they are actually published publicly, not only present in a local `~/.m2` or a private registry.
- Watch for dependencies pinned to `SNAPSHOT`/pre-release versions, `file:`/`link:` paths, or git URLs that an outsider could not access.

Pass: all dependencies resolve from public sources. Warning: relies on pre-release/SNAPSHOT versions that exist publicly but are unstable. Fail: one or more dependencies are unreachable for an outside consumer (private registry, unpublished internal artifact, local path).

## Check 5 — Documentation present

Public API surface should be documented so consumers can use it without reading the implementation.

- **Java**: public classes, interfaces, and public/protected methods carry Javadoc. Verify the Javadoc *build* succeeds (e.g., `./mvnw javadoc:javadoc`) — a failing doc build is itself a finding. Sample across packages rather than assuming uniformity.
- **TypeScript**: exported functions, classes, and types carry TSDoc/JSDoc comments.
- Judge proportionally: a thin internal module needs less than a published library's public API. State the basis for your judgment.

Pass: public API broadly documented and the doc build (if any) succeeds. Warning: notable gaps. Fail: largely undocumented public API, or the documentation build fails.

## Check 6 — Documentation correct (does not contradict the code)

Documentation that lies is worse than none — it actively misleads. Presence (Check 4) is necessary but not sufficient; this check verifies *truthfulness*. This is the check that most needs real reading of code, so do not shortcut it.

Sample a meaningful set of documented public elements and compare the documentation against the actual code:

- **Signatures match**: every `@param` names a parameter that still exists; no parameter is undocumented; `@return` describes what is actually returned (and isn't present on `void`); `@throws` lists exceptions the code can actually throw.
- **Described behavior matches implementation**: the prose describes what the method really does — not an earlier version of it. Watch for docs that describe removed behavior, wrong default values, or conditions that no longer hold.
- **Examples are valid**: code snippets in docs would actually compile and reflect the current API (correct method names, argument order, types).
- **References resolve**: linked types/classes (`{@link ...}`, cross-references) still exist.

Report each contradiction concretely with `file:line`, what the docs claim, and what the code actually does — this is the highest-signal output of the whole audit.

Pass: sampled docs are accurate. Warning: minor staleness. Fail: documentation materially contradicts the code.

## Check 7 — Test coverage meets the threshold

Coverage is a floor, not a goal — but below the floor, the project is under-tested. Run the tests *with coverage* and read the real numbers; never estimate.

- **Java**: run with JaCoCo (e.g., `./mvnw clean verify` with the JaCoCo plugin configured, or `./mvnw jacoco:report`) and read the generated report.
- **TypeScript**: run `pnpm test --coverage` (or `vitest run --coverage` / `jest --coverage`) and read the summary.
- **Thresholds** (from `../../conventions/software-quality.md`): **backend ≥ 80%**, **frontend ≥ 70%**. For a fullstack project, evaluate each side against its own threshold.

If coverage tooling is not configured, that is itself a finding (you cannot verify coverage) — report it and do not guess a number.

Pass: at or above the threshold. Warning: within a few points below. Fail: clearly below, or coverage cannot be measured because no tooling is configured.

## Check 8 — Secret safety and repository hygiene

Making a repo public is irreversible in a way code is not: every committed secret and every embarrassing leftover becomes part of the permanent, clonable history. A secret cannot be un-leaked by deleting it later — it must be rotated. So this check asks the blunt question: **is there anything in here that should not be public?** Confirming that `.env` is gitignored is *not* enough on its own — the dangerous secrets are the ones hiding in files that *are* tracked.

**Scan tracked files for committed secrets.** Use `git ls-files` to enumerate what is actually in the repo, and inspect the high-risk files directly: `.env.example` and other sample/config files, CI workflow YAML, Docker/compose files, and documentation. Look for values that look like real credentials rather than placeholders:

- API keys and tokens by their tell-tale prefixes (`sk-`, `AKIA`, `ghp_`/`gho_`, `xox`-, `AIza`, etc.), long random hex/base64 strings, and `-----BEGIN ... PRIVATE KEY-----` blocks.
- Distinguish a *real-looking* value from an obvious placeholder. A `.env.example` is supposed to contain only placeholders (`changeme`, `your-api-key`, `<token>`); a value like `TRANSLATION_API_KEY=sk-ksmcjk...` sitting next to real placeholders is a red flag precisely because it does not look like the others.
- Verify `.env` (and any other real secret-bearing file) is both gitignored **and** not tracked (`git ls-files | grep -i env`).

A committed credential that appears real is a **release blocker (Fail)** — flag it, and recommend rotating it at the provider, not just deleting the line (it is already in history).

**Repository hygiene.** Skim the tracked file list for things that should not ship publicly: corrupted or accidentally-committed files (e.g. a tool error message pasted into a doc), internal scratch notes (`TODO.md`, scratchpads, AI-handoff files) that may not be meant for an external audience, and large binaries that do not belong. These are usually warnings, not blockers, but a release is the moment to decide deliberately what is public.

Pass: no committed secrets and nothing inappropriate for a public repo. Warning: tracked internal/scratch files worth a deliberate keep-or-remove decision. Fail: a real-looking credential is committed.

### Additional Open Elements standards (check, but weight as secondary)

These round out release-readiness and come from `../../conventions/software-quality.md`. Report them, but a project can still be broadly healthy with warnings here:

- **SBOM**: the build produces a CycloneDX SBOM (e.g., the `cyclonedx-maven-plugin` is configured).
- **CI**: `.github/workflows/` builds and tests on pull requests.
- **Required root files**: `LICENSE`, `README.md`, `CODE_OF_CONDUCT.md`, `CONTRIBUTING.md`, `.gitignore`, `.editorconfig` (see the `project-setup` skill). A `SECURITY.md` is also expected for projects with an authentication or credential surface.

### Phase 3 — Write the report

## Report format

Present the report in the conversation. Offer to save it to `docs/quality-audit.md` only if the user wants a persisted artifact (writing that file is the one exception to read-only, and only on request).

Use this structure:

```
# Project Quality Audit — <project name>

## Verdict
<PASS | PASS WITH WARNINGS | FAIL> — one-sentence summary.

## Summary table
| # | Check | Status | Note |
|---|-------|--------|------|
| 1 | README completeness            | ✅/⚠️/❌/⏭️ | … |
| 2 | License                        | … | … |
| 3 | Buildable as documented        | … | … |
| 4 | Dependencies resolvable        | … | … |
| 5 | Documentation present          | … | … |
| 6 | Documentation correct          | … | … |
| 7 | Test coverage                  | … | <actual %> vs <threshold> |
| 8 | Secret safety & repo hygiene   | … | … |
|   | Additional standards           | … | … |

## Findings
For each check that is not a clean pass, a subsection with:
- What was checked and how (command run, files inspected)
- Concrete evidence (`file:line`, command output excerpts, measured numbers)
- Why it matters
- Recommended fix (concrete and actionable)

## What's solid
Briefly note what already meets the bar — an audit should also confirm strengths, not only flag gaps.
```

Status legend: ✅ Pass · ⚠️ Warning · ❌ Fail · ⏭️ Not applicable (with reason).

The overall verdict is **FAIL** if any of Checks 1–8 fails, **PASS WITH WARNINGS** if there are only warnings, **PASS** if everything is clean. A committed real-looking secret (Check 8) is always a release blocker, regardless of how strong everything else is. Be precise and evidence-based: every Fail or Warning must cite what you actually observed (a command's output, a measured coverage number, a `file:line`). Vague findings ("docs could be better") are not useful — the value of this audit is concrete, verifiable claims the team can act on.

## Notes

- This audit runs real commands. Builds and test suites can be slow; tell the user when you start a long-running step so the wait is expected.
- Stay read-only. The only file you may create is the optional `docs/quality-audit.md`, and only when the user asks for it.
- Reuse rather than duplicate: lean on `reproducible-builds-check` for build determinism, `quality-review` for diff-level code quality, and `project-setup` for the canonical list of required repository files.
