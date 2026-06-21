---
name: todo-capture
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Quickly capture a half-formed idea, follow-up, or deferred item as a TODO entry in docs/TODO.md, without interrupting the current work. Use this when something worth keeping surfaces during spec creation, a grill session, a review, or general work but is not yet clear enough to become a spec or GitHub issue — e.g. "note this as a TODO", "capture this for later", "add a TODO", "let's not forget X", "park this idea", "track this follow-up". Records the idea, where it came from, and any prerequisites so it is not lost.
argument-hint: [the idea, follow-up, or deferred item to record]
---

# Capture a TODO

Record a half-formed idea, deferred item, or follow-up in a lightweight project backlog at `docs/TODO.md`, so it is not lost. This is the low-ceremony counterpart to `/spec-create`: a TODO is something worth keeping that is **not yet ready** to become a specification or a GitHub issue. The goal is a fast capture that does not interrupt the work in progress.

A TODO entry is a candidate, not a commitment. When it matures, it graduates into a spec (via `/spec-create`) or a GitHub issue — see [Lifecycle](#5-lifecycle).

## When to use this vs. a spec or issue

- **Capture as a TODO** when the item is half-formed, deferred for scope reasons, or a "don't forget this" follow-up — there is not enough clarity yet to design it, and no need to act on it now.
- **Do not bury a ready item in the backlog.** If the item is already well-defined and worth acting on, recommend `/spec-create` (to plan it) or drafting a GitHub issue instead. `docs/TODO.md` is for things that are *not yet ready*.

## Instructions

### 1. Gather the item

Capture the item from the current context — for example a scope split during `/spec-create`, an assumption or follow-up surfaced by `/grill-me`, a finding from `/quality-review`, or just something noticed during general work — or from the user's free-text description.

Note, as far as it is known:

- **The core idea** — what it is and why it matters (one or two sentences).
- **Provenance** — where it surfaced (which spec, grill session, review, or conversation) and **why it is being deferred** rather than acted on now.
- **Prerequisites** — any spec or work that must land first.
- **Concrete pointers** — spec numbers, file paths, class names, or doc references that will help whoever picks it up later.

Keep this quick — it is a scratch capture, not a design session. Ask at most one clarifying question, and only if the title or the intent is genuinely unclear.

### 2. Confirm it belongs in the backlog

If, while gathering it, the item turns out to be well-defined and ready to be worked on, do not file it as a TODO — recommend `/spec-create` or drafting a GitHub issue instead. Proceed only once it is clear this is a "park for later" item.

### 3. Locate `docs/TODO.md`

- The backlog lives at **`docs/TODO.md`**, alongside the project's other documentation under `docs/`.
- If it does not exist, create it with a single `# TODO` heading.
- If a legacy `TODO.md` exists at the repository root, offer to move it into `docs/` first, so there is exactly one backlog.
- Read the existing file to match its formatting **and to avoid duplicates** — if a closely related entry already exists, extend or update it rather than adding a near-duplicate.

### 4. Write the entry

Append a new entry at the end of `docs/TODO.md` using this format:

```markdown
## <Short title>

<One short paragraph: what the idea is and why it matters.>

- <Optional concrete detail>
- <Optional concrete detail>

**Context:** <Where this surfaced and why it was deferred — reference the spec, issue, grill session, or review it came from.>

**Prerequisite:** <Dependent spec(s) or work that must land first — omit this line if there are none.>
```

Rules for the content:

- **Title** — short and descriptive; an imperative phrase or a noun phrase.
- **Description** — one short paragraph stating what and why. Add bullet points only when the item has concrete sub-points worth listing; omit them otherwise.
- **Context** is mandatory — it records provenance and the reason for deferral (e.g. "Deferred from spec 009 (contact-company cross-navigation)", "Surfaced during the grill session for spec 075"). This is what lets a reader judge the item later.
- **Prerequisite** is optional — include it only when another spec or piece of work must land first.
- Reference **real artifacts** — spec folders, file paths, class names — so the item stays actionable.
- Write entries in **English**, for consistency with the rest of `docs/` (the same convention as spec-driven development).

### 5. Lifecycle

`docs/TODO.md` is a living backlog of *open* items — keep it that way:

- When an item matures, **promote it**: run `/spec-create` (referencing the TODO) to plan it, or draft a GitHub issue.
- Once an item has been promoted into a spec or otherwise resolved, **remove its entry** so the backlog reflects only what is still open. If you want to preserve the trail, strike it through instead and point to where it went — e.g. `~~Old item~~ — implemented via spec 012`.

### 6. Summary

After writing the entry, confirm briefly:

- The entry title and that it now lives in `docs/TODO.md`.
- A reminder that it can later be promoted via `/spec-create` or turned into a GitHub issue when it is ready.
