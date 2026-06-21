---
name: project-analyze
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Analyze the current project and generate or update the Project Context section in the project's CLAUDE.md (features, tech stack, structure, architecture). Use this skill when setting up claude-project-base in a new project, or when the project has changed significantly and the documentation needs to be refreshed.
---

# Analyze Project

Scan the current project and generate or update the **Project Context** section in the project's `CLAUDE.md` — the single source of truth that gives Claude Code persistent context about the project.

## Target Sections

All content lives in the `## Project Context` section near the top of the project's `CLAUDE.md` (at the project root), as four subsections:

- `### Features` — Core features and user-facing capabilities
- `### Tech Stack` — Languages, frameworks, libraries, databases, external services
- `### Structure` — Repository layout and directory structure
- `### Architecture` — Technical architecture, component interactions, data flow

Update only these four subsections. Leave the rest of `CLAUDE.md` (base rules, conventions, project-specific rules) untouched. If `CLAUDE.md` has no `## Project Context` section yet, add it directly below the file's intro, before the first rules section.

## Instructions

### 1. Understand the codebase

Build a thorough understanding of the entire codebase before generating any documentation. Shallow scanning of config files alone is not sufficient — the goal is to understand what the project does, how it is structured, and how its parts relate to each other.

Explore and understand:

- **Root files** — Read `README.md`, `package.json`, `pom.xml`, `build.gradle`, `docker-compose.yml`, `Dockerfile`s, `.sdkmanrc`, `.nvmrc`, and similar configuration files
- **Project structure** — Modules, packages, and how the codebase is organized. List top-level directories and key sub-directories to understand the layout
- **Existing functionality** — What the application does, which features exist, and how they work. Read key entry points (main classes, app routers, index files) and follow the code paths to understand behavior
- **Dependencies between components** — How modules, services, and layers interact with each other. Trace the relationships between packages, classes, and modules to understand the dependency graph
- **Patterns and conventions** — Architectural patterns in use (e.g., layered architecture, event-driven, plugin-based), naming conventions, and coding style
- **External dependencies** — Read dependency files (pom.xml, package.json, requirements.txt) to identify frameworks, libraries, and third-party services the project relies on
- **Data model** — Existing database schemas, entities, and data flows
- **Configuration and infrastructure** — Build system, deployment setup, and environment configuration
- **Existing documentation** — Read any existing docs, ADRs, or architecture notes
- **Specifications** — Check if `docs/specs/INDEX.md` exists. If it does, read the index and then read the `design.md` and `behaviors.md` files of all specs with status `done` or `implemented`. These specs capture the *reasoning* behind design decisions — why the code is structured the way it is, what alternatives were considered, and what constraints drove the design. This context is essential for producing architecture documentation that explains not just *what* the code does but *why* it was built that way.
- **CI/CD** — Check `.github/workflows/` to understand the build and deployment pipeline

Use the Explore agent or read key files directly. Invest the time to read actual source code, not just configuration — understanding the real functionality and modularization is essential for producing accurate project documentation.

### 2. Read the existing Project Context

Read the project's current `CLAUDE.md`. If the `## Project Context` subsections already contain content (not just placeholder comments), use it as the starting point. The goal is to **update** the section, not to start from scratch. Preserve any manually added details that are still accurate, and never touch the rest of `CLAUDE.md`.

### 3. Generate or update each subsection

Write each subsection with concrete, factual content based on what was found. Follow these guidelines:

**`### Features`:**
- Start with a 2–3 sentence overview of what the project is and who it is for
- List core features as bullet points with brief descriptions
- Focus on what the project *does*, not how it is built

**`### Tech Stack`:**
- List languages with versions (from .sdkmanrc, .nvmrc, pom.xml, package.json)
- List frameworks and their versions
- List build tools and package managers
- List databases, caches, message brokers, and external services
- List key libraries (logging, testing, ORM, etc.)

**`### Structure`:**
- Show the repository layout as a tree diagram (top-level + one or two levels deep for important directories)
- Describe what each key directory contains
- Note where to find entry points, configuration, tests, and documentation

**`### Architecture`:**
- Describe the main components and their responsibilities
- Explain how components communicate (REST, gRPC, message queues, JDBC, etc.)
- Include a Mermaid diagram showing the high-level architecture
- Note any important architectural decisions or patterns (e.g., event sourcing, CQRS, microservices)
- Where finished specs exist, include the **rationale** behind key design decisions — explain *why* the architecture is the way it is, not just *what* it looks like. Reference the spec ID (e.g., "see spec 003") so readers can find the full context

Keep entries concise — this content is loaded into context on every interaction, so excessive detail dilutes the more important rules.

### 4. Check if README.md needs updating

Compare the project's `README.md` against what was learned about the project. Check whether the README accurately reflects:
- The project's purpose and features
- Setup and installation instructions
- Tech stack and prerequisites
- Architecture overview (if present)

If the README is outdated, incomplete, or missing important information, note the specific discrepancies and include them in the summary presented to the user. Do not silently skip README issues.

### 5. Present changes to the user

Show a summary of what was found and what will be written. If the Project Context already had content, highlight what changed. If the README needs updating, list the specific issues found and propose changes. Ask the user to review before writing.

### 6. Write the Project Context

After user confirmation, update the four subsections of the `## Project Context` section in `CLAUDE.md`. Replace the placeholder HTML comments with real content — do not leave comments or placeholder text behind, and do not modify any other part of `CLAUDE.md`. If the user approved README changes, update the README as well.
