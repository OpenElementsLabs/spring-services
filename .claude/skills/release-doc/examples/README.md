# Reference examples for `release-doc`

Gold-standard, self-contained samples for the `release-doc` skill. Read the one that matches the document you are producing, then mirror its structure. They are trimmed and illustrative — the linked live docs are the authoritative source.

| Example | Demonstrates | Live canonical doc |
|---------|--------------|--------------------|
| [application-release-notes.md](application-release-notes.md) | Application release note for operators: highlights, under-the-hood, upgrade notes (incl. a destructive removal). | [open-crm v1.4](https://github.com/OpenElementsLabs/open-crm/blob/main/docs/releases/v1.4.md) |
| [library-upgrade-breaking.md](library-upgrade-breaking.md) | AI-executable library upgrade with **Breaking + Breaking-light + Additive** changes, ordered DB migration, guard rails. | [spring-services 0.16](https://github.com/OpenElementsLabs/spring-services/blob/main/docs/upgrade-to-0.16.md) |
| [library-upgrade-additive.md](library-upgrade-additive.md) | AI-executable library upgrade for a **purely additive** release (do-nothing-and-it-still-works). | [open-elements-ui 0.6](https://github.com/OpenElementsLabs/open-elements-ui/blob/main/docs/upgrade-to-0.6.md) |

The full rules these examples follow live in the `release-doc` skill (`../SKILL.md`).
