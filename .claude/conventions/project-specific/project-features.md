# Project Features

## Overview

Spring Services is a reusable Spring Boot library providing common backend services for web applications: user management, API key authentication, tagging, webhooks, settings storage, and multi-tenancy. It is designed to be included as a dependency in Spring Boot applications.

## Core Features

- **User Management** — OAuth2/JWT-based user resolution with avatar upload/download (size and type validation)
- **API Key Authentication** — Secure API key generation, SHA-256 hashing, prefix display, and custom Spring Security filter (`X-API-Key` header)
- **Tag Service** — CRUD for categorization tags with unique name constraint, color, and description
- **Webhook System** — Event-driven webhook delivery (async HTTP POST) triggered by create/update/delete events, with status tracking and error handling
- **Settings Service** — Key-value store for application configuration
- **Multi-Tenancy** — Tenant isolation via JWT principal, with base entity and service classes that enforce tenant boundaries
- **Event System** — Spring `ApplicationEvent`-based pub/sub for data lifecycle events (create, update, delete)
- **Release Pipeline** — Local release to Maven Central via JReleaser, automatic SNAPSHOT publishing to GitHub Packages, CI build validation on PRs