<!--
Reference example for the `release-doc` skill — an APPLICATION release note.
Modeled on the canonical doc:
https://github.com/OpenElementsLabs/open-crm/blob/main/docs/releases/v1.4.md
Illustrative and trimmed; consult the linked doc for the authoritative version.
File would live at: docs/releases/v1.4.md
-->

# Open CRM v1.4 – Release Notes

**Released:** 2026-05-04
**GitHub release:** [v1.4](https://github.com/OpenElementsLabs/open-crm/releases/tag/v1.4)
**Previous version:** [v1.3](https://github.com/OpenElementsLabs/open-crm/releases/tag/v1.3) (2026-04-30)

This release focuses on **user interface improvements**: a new Markdown editor for comments and forms, more consistent list and action controls, and small adjustments to existing features. It also includes one significant content change: the Tasks module has been removed (see below).

---

## Highlights for Admins and Users

### New Markdown editor in comments and forms

Comments on contacts and companies, as well as the detail forms, now support **Markdown formatting** (bold, italic, lists, links, headings). Existing plain-text comments remain readable and continue to display correctly. This makes longer notes far easier to scan.

### Tasks module removed

The **Tasks module has been removed from Open CRM** — the tasks overview, creating/editing/commenting on tasks, and the "Create task" links on company and contact pages.

**What admins should be aware of:**

- Data still present in the tasks tables is no longer reachable from the UI. If it needs to be archived, create a database export **before** updating.
- The navigation menu no longer contains the Tasks entry.
- The feature is planned to return in a revised form in a future release.

### Unified list pagination

All paginated lists (Contacts, Companies, Tags, API Keys, Webhooks) now share the same look and behaviour: a consistent total-count label with correct singular/plural handling (EN/DE), an always-visible page-size selector, and a remembered page size. This removes earlier inconsistencies between screens.

---

## Other changes under the hood

These are not directly visible to end users but are useful to know:

- **Updated libraries:** Open CRM now uses `@open-elements/ui` 0.6.0; reusable components (dialogs, buttons, Markdown editor, pagination) were extracted into the shared library.
- **Leaner frontend build:** unused dependencies removed, reducing install time and bundle size.
- **Expanded test coverage** for comments and contact detail pages.

---

## Upgrade notes

- **No database migrations** are required for this update. The schemas for contacts, companies, tags, API keys, webhooks, comments, and audit logs are unchanged.
- **Anyone actively using the Tasks module** should export the tasks tables before updating — they are no longer reachable through the UI.
- Configuration (SSO, sync, translation API) remains fully compatible.

---

## Full commit history

The complete technical list of changes is available in the Git history between the tags:

```bash
git log v1.3..v1.4 --oneline
```
