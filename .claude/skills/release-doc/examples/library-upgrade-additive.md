<!--
Reference example for the `release-doc` skill — a LIBRARY upgrade guide for a
purely ADDITIVE release. Modeled on the canonical doc:
https://github.com/OpenElementsLabs/open-elements-ui/blob/main/docs/upgrade-to-0.6.md
Illustrative and trimmed; consult the linked doc for the authoritative version.
File would live at: docs/releases/upgrade-to-0.6.md
-->

# Upgrade prompt: `@open-elements/ui` 0.5.x → 0.6.0

## Prompt

You are working inside an app that depends on `@open-elements/ui`. This release is **purely additive**: six new components are now exported and no existing API changed. Your job is to bump the dependency and, where the consumer has hand-rolled local copies of the new components, replace those copies with the library versions. Nothing breaks if you do nothing beyond the version bump.

### What changed in 0.6.0

#### Dependencies

Bump `@open-elements/ui` to `^0.6.0` in the consumer's frontend `package.json`. `peerDependencies` did **not** change in 0.6.0 — leave them as they are. No other version bumps.

#### Additive: six new exported components

`Dialog`, `Pagination`, `MarkdownEditor`, `IconButton`, `EmptyState`, and `Tooltip` are now exported from `@open-elements/ui`. All existing imports keep working unchanged — this is the safe, do-nothing-and-still-works case.

If the consumer maintains a local component that duplicates one of these (same role, not just the same name), it can be deleted and re-imported from the library:

```ts
// before — local duplicate
import { MarkdownEditor } from "@/components/markdown-editor";

// after — library export
import { MarkdownEditor } from "@open-elements/ui";
```

### Steps

1. Find the consumer's frontend `package.json`; bump `@open-elements/ui` to `^0.6.0`; install.
2. Search the codebase for local components that match a new export by role (not merely by name).
3. Verify behavioural parity between the local component and the library version before replacing.
4. Delete the verified-duplicate local component and rewrite its imports to `@open-elements/ui`.
5. Convert any type-only re-exports to `import type`.
6. Verify compilation and run the test suite.
7. Commit.

### Guard rails

- Do not change the public API of the consumer app while doing this upgrade.
- Do not delete a local component just because its name matches a new export — verify behavioural parity first.
- Do not remove `peerDependencies` — they did not change in 0.6.0.
- Do not bump unrelated dependency versions in the same change.

### Don't do this

- Do not refactor existing components that consume the new exports.
- Do not edit `@open-elements/ui` from the consumer side.
- Do not bundle this upgrade with feature work in the same PR.
