---
name: project-analyze
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Analyze the current project and generate or update the project-specific documentation files (features, tech stack, structure, architecture). Use this skill when setting up claude-project-base in a new project, or when the project has changed significantly and the documentation needs to be refreshed.
---

# Analyze Project

Scan the current project and generate or update the four project-specific documentation files that give Claude Code persistent context about the project.

## Target Files

All files live in `conventions/project-specific/` relative to this skill (at `../../conventions/project-specific/`):

- `project-features.md` — Core features and user-facing capabilities
- `project-tech.md` — Languages, frameworks, libraries, databases, external services
- `project-structure.md` — Repository layout and directory structure
- `project-architecture.md` — Technical architecture, component interactions, data flow

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
- **Specifications** — Check if `specs/INDEX.md` exists. If it does, read the index and then read the `design.md` and `behaviors.md` files of all specs with status `done` or `implemented`. These specs capture the *reasoning* behind design decisions — why the code is structured the way it is, what alternatives were considered, and what constraints drove the design. This context is essential for producing architecture documentation that explains not just *what* the code does but *why* it was built that way.
- **CI/CD** — Check `.github/workflows/` to understand the build and deployment pipeline

Use the Explore agent or read key files directly. Invest the time to read actual source code, not just configuration — understanding the real functionality and modularization is essential for producing accurate project documentation.

### 2. Read existing project-specific files

If the four target files already contain content (not just placeholder comments), read them first. The goal is to **update** them, not to start from scratch. Preserve any manually added details that are still accurate.

### 3. Generate or update each file

Write each file with concrete, factual content based on what was found. Follow these guidelines:

**`project-features.md`:**
- Start with a 2–3 sentence overview of what the project is and who it is for
- List core features as bullet points with brief descriptions
- Focus on what the project *does*, not how it is built

**`project-tech.md`:**
- List languages with versions (from .sdkmanrc, .nvmrc, pom.xml, package.json)
- List frameworks and their versions
- List build tools and package managers
- List databases, caches, message brokers, and external services
- List key libraries (logging, testing, ORM, etc.)

**`project-structure.md`:**
- Show the repository layout as a tree diagram (top-level + one or two levels deep for important directories)
- Describe what each key directory contains
- Note where to find entry points, configuration, tests, and documentation

**`project-architecture.md`:**
- Describe the main components and their responsibilities
- Explain how components communicate (REST, gRPC, message queues, JDBC, etc.)
- Include a Mermaid diagram showing the high-level architecture
- Note any important architectural decisions or patterns (e.g., event sourcing, CQRS, microservices)
- Where finished specs exist, include the **rationale** behind key design decisions — explain *why* the architecture is the way it is, not just *what* it looks like. Reference the spec ID (e.g., "see spec 003") so readers can find the full context

### 4. Check if README.md needs updating

Compare the project's `README.md` against what was learned about the project. Check whether the README accurately reflects:
- The project's purpose and features
- Setup and installation instructions
- Tech stack and prerequisites
- Architecture overview (if present)

If the README is outdated, incomplete, or missing important information, note the specific discrepancies and include them in the summary presented to the user. Do not silently skip README issues.

### 5. Present changes to the user

Show a summary of what was found and what will be written. If the files already had content, highlight what changed. If the README needs updating, list the specific issues found and propose changes. Ask the user to review before writing any files.

### 6. Write the files

After user confirmation, write the four project-specific files. If the user approved README changes, update the README as well. Do not include HTML comments or placeholder text — only real content.
