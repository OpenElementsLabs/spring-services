---
name: mkdocs-setup
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Set up, review, or extend project documentation built with MkDocs and the Material theme, published to GitHub Pages. Use this skill whenever the user wants to add a `docs/` folder, create or modify `mkdocs.yml`, configure the Material theme, add Mermaid diagrams, set up local doc preview, deploy docs to GitHub Pages, or wire up PR preview deployments. Trigger this skill even if the user only mentions "documentation site", "MkDocs", "Material theme", "docs preview", "GitHub Pages for docs", or "ADRs in the repo" without explicitly naming MkDocs.
---

# MkDocs Project Documentation Setup

This skill defines the conventions for technical project documentation at Open Elements. We use [MkDocs](https://www.mkdocs.org/) with the [Material for MkDocs](https://squidoc.github.io/mkdocs-material/) theme. Docs live in the repository alongside the code and are published as GitHub Pages.

A reference implementation is the [maven-initializer docs](https://github.com/support-and-care/maven-initializer/tree/main/docs).

This skill covers project documentation only. Auto-generated API docs (Javadoc, TypeDoc) and the user-facing `README.md` are out of scope and have their own tooling and conventions.

## When to use this skill

Use this skill when the user:

- Adds technical documentation to a project for the first time
- Reviews or extends an existing `docs/` folder or `mkdocs.yml`
- Wants to deploy documentation to GitHub Pages
- Wants PR preview deployments for doc changes
- Adds Architecture Decision Records (ADRs) or architecture overviews

For the GitHub Actions workflow that deploys docs (`docs.yml`), see the `github-actions-setup` skill.

## Instructions

1. **Check if docs already exist.** Look for a `docs/` folder and `mkdocs.yml` at the repository root before creating anything new. If present, review them against the conventions below.

2. **Set up the repository structure** using the layout shown in [Repository Structure](#repository-structure).

3. **Create or update `mkdocs.yml`** at the repository root. At minimum it must configure the Material theme, an explicit `nav` section, the `search` plugin, and `pymdownx.superfences` for Mermaid support.

4. **Write content in plain Markdown.** Use GitHub Flavored Markdown (GFM). Prefer Mermaid diagrams over external image files for architecture and flow visualizations.

5. **Verify local preview works** by recommending the user runs:

   ```bash
   pip install mkdocs-material "pymdown-extensions"
   mkdocs serve
   ```

   The site is then available at `http://127.0.0.1:8000`.

6. **Wire up GitHub Pages deployment** via the `github-actions-setup` skill (it provides the `docs.yml` workflow). Ensure GitHub Pages is enabled on the repository with the source set to the `gh-pages` branch.

## Repository Structure

```
project-root/
├── docs/
│   ├── index.md              # Landing page
│   ├── architecture.md       # Architecture overview
│   ├── contributing.md       # How to contribute to the docs
│   └── stylesheets/          # Custom CSS (optional)
└── mkdocs.yml                # MkDocs configuration at repository root
```

## MkDocs Configuration

The `mkdocs.yml` lives at the repository root and configures:

- **Theme**: Material for MkDocs with light/dark mode toggle.
- **Navigation**: Explicit `nav` section defining the page hierarchy.
- **Extensions**: Markdown extensions for features like Mermaid diagrams (`pymdownx.superfences`).
- **Plugins**: At minimum the `search` plugin.

## Content Guidelines

- Write documentation in plain Markdown inside the `docs/` folder.
- The `index.md` serves as the landing page with links to the main sections.
- Keep documentation close to the code — update docs when the related code changes.
- Use Mermaid diagrams for architecture and flow visualizations instead of external image files where possible.

## GitHub Pages Deployment

Documentation is deployed automatically via a GitHub Actions workflow (`.github/workflows/docs.yml`, provided by the `github-actions-setup` skill):

- **Pushes to main**: Deploy to the production site root using `mkdocs gh-deploy --force`.
- **Pull requests**: Build a preview and deploy it to a `/pr/<number>/` subdirectory. The workflow comments on the PR with a link to the preview.

### Requirements

- GitHub Pages must be enabled on the repository with the source set to the `gh-pages` branch.
- The deployment workflow needs `contents: write` and `pull-requests: write` permissions.

## What to Document

- Architecture overview (components, their responsibilities, how they interact).
- Architecture Decision Records for significant technical choices.
- Setup and contribution instructions.
- API documentation if the project exposes a public API.

## What NOT to Document in MkDocs

- User-facing README content — that stays in `README.md` at the repository root.
- Auto-generated API docs (Javadoc, TypeDoc) — those have their own tooling.
- Temporary notes or work-in-progress — use issues or discussions instead.
