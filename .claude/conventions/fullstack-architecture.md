# Fullstack Architecture Conventions for Claude Code

## Overview

In Open Elements projects that have both a frontend and a backend,
both parts are treated as fully independent applications within a single repository.
They share no code, no build process, and no runtime.
The only coupling is at the network level through APIs.

Reference implementations of this architecture:

- [maven-initializer](https://github.com/support-and-care/maven-initializer) — Fullstack application without authentication andf without database.
- [application-skeleton](https://github.com/OpenElementsLabs/application-skeleton) — Fullstack application starter with OAuth2/OIDC authentication (compatible with Authentik, Keycloak, and other OIDC providers), mock OAuth2 server for local development, and `docker-compose.override.yml` pattern for dev/prod separation. Designed for deployment on Open Elements Coolify infrastructure.

## Repository Structure

```
project-root/
├── backend/              # Independent backend application
│   ├── Dockerfile        # Standalone container build
│   ├── src/              # Backend source code
│   └── ...               # Backend build files (pom.xml, build.gradle, etc.)
├── frontend/             # Independent frontend application
│   ├── Dockerfile        # Standalone container build
│   ├── src/              # Frontend source code
│   └── ...               # Frontend build files (package.json, etc.)
├── docker-compose.yml    # Orchestration for local development and deployment
└── README.md
```

## Core Principles

- **IMPORTANT — Full independence**: Backend and frontend are separate applications. Each has its own source code, dependencies, build process, and configuration. There are no shared modules, monorepo tooling, or cross-references between them.
- **Separate containers**: Each application has its own `Dockerfile` in its directory. Each container can be built and run independently.
- **Docker Compose for orchestration**: A `docker-compose.yml` at the repository root wires the containers together. It handles port mapping, environment variables (like `BACKEND_URL` for the frontend), and startup ordering.
- **Independent local development**: Each application can be started on its own for development without Docker. The backend and frontend each have their own dev server and can be run in separate terminals.

## Docker

### Dockerfiles

Each application has a multi-stage `Dockerfile` in its own directory:

- **Backend (Java/Spring Boot)**: Build stage compiles with Maven/Gradle, runtime stage uses a minimal JRE image.
- **Frontend (TypeScript/Next.js)**: Build stage installs dependencies and compiles, runtime stage serves the built application with a minimal Node.js image. **IMPORTANT**: The backend is not available during `next build` in the Docker build stage. Pages that fetch data from the backend must not be statically pre-rendered at build time, or they will cache an error state. Use `dynamic = 'force-dynamic'` or equivalent mechanisms to ensure these pages are rendered at request time.
  - **IMPORTANT**: Next.js evaluates `next.config.ts` (including `rewrites()`) at **build time**, not at runtime. Environment variables like `BACKEND_URL` that are only set via `environment:` in `docker-compose.yml` are not available during `docker build`. The rewrite rules get baked into the build artifact with the fallback value (typically `localhost:8080`), which points to the container itself — not the backend service. To fix this, the frontend `Dockerfile` must declare `BACKEND_URL` as a build argument and set it as an environment variable before `RUN pnpm build`:
    ```dockerfile
    ARG BACKEND_URL=http://backend:8080
    ENV BACKEND_URL=${BACKEND_URL}
    RUN pnpm build
    ```
    The `docker-compose.yml` should pass the value via `build.args`:
    ```yaml
    frontend:
      build:
        context: ./frontend
        args:
          BACKEND_URL: http://backend:8080
    ```

Both Dockerfiles should:

- Use multi-stage builds to keep the final image small.
- Pin base image versions (e.g., `eclipse-temurin:21-alpine`, `node:22-alpine`). **IMPORTANT**: The Java version in the Docker base image must match the Java version in the project's `pom.xml` (`<java.version>` / `<maven.compiler.release>`) and must be supported by the framework in use (e.g., Spring Boot). Check the framework's documentation for supported Java versions before choosing a version. Use the same Java version consistently across `.sdkmanrc`, `pom.xml`, and Dockerfile.
- Run the application as a non-root user in the final stage.
- Expose only the application port.
- **IMPORTANT**: Every `COPY` instruction in a Dockerfile must reference files or directories that are guaranteed to exist. Do not use shell-style workarounds like `2>/dev/null || true` — these do not work in Dockerfile `COPY` instructions and will cause build failures. For the Next.js frontend, ensure a `public/` directory exists in the project (at minimum with a `favicon.ico` or an empty `.gitkeep` file).
- **IMPORTANT**: Every application directory that has a `Dockerfile` must also have a `.dockerignore` file to exclude build artifacts and dependencies from the Docker context. Without it, the Docker context can be hundreds of MB and builds will be slow.

  **Backend `.dockerignore`:**
  ```
  target/
  .idea/
  *.iml
  .git
  ```

  **Frontend `.dockerignore`:**
  ```
  node_modules/
  .next/
  .idea/
  .git
  ```

### Docker Compose

The `docker-compose.yml` at the repository root:

- Defines one service per application (`backend`, `frontend`).
- Uses `build` with the application directory as context.
- Maps internal ports to configurable external ports via environment variables with defaults.
- Sets environment variables to connect services (e.g., `BACKEND_URL` on the frontend).
- Uses `depends_on` to define startup order where needed.

Example structure:

```yaml
services:
  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "${DB_PORT:-5432}:5432"

  backend:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME}
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
    ports:
      - "${BACKEND_PORT:-9081}:8080"
    depends_on:
      - db

  frontend:
    build:
      context: ./frontend
      args:
        BACKEND_URL: http://backend:8080
    ports:
      - "${FRONTEND_PORT:-4001}:3000"
    depends_on:
      - backend
```

The corresponding `.env.example` should contain:

```env
DB_NAME=appdb
DB_USER=appuser
DB_PASSWORD=changeme
BACKEND_PORT=9081
FRONTEND_PORT=4001
```

### Docker Compose Override for Local Development

Use a `docker-compose.override.yml` file to add development-only services and configuration that should not be part of the production setup. Docker Compose automatically merges this file with `docker-compose.yml` when running `docker-compose up`.

Typical use cases for the override file:

- **Port mappings**: Expose service ports for local access (the base `docker-compose.yml` should not expose ports when the production deployment runs behind a reverse proxy).
- **Mock services**: Add mock servers for external dependencies (e.g., a mock OAuth2 server for local authentication testing).
- **Debug configuration**: Additional environment variables for development mode, verbose logging, or hot-reload.
- **Host networking**: `extra_hosts: ["localhost:host-gateway"]` to allow containers to reach the host machine.

Example structure:

```yaml
# docker-compose.override.yml — Development overrides (not committed or gitignored)
services:
  db:
    ports:
      - "${DB_PORT:-5432}:5432"

  backend:
    ports:
      - "${BACKEND_PORT:-9081}:8080"
    environment:
      SOME_DEV_OVERRIDE: "dev-value"

  frontend:
    ports:
      - "${FRONTEND_PORT:-4001}:3000"
    extra_hosts:
      - "localhost:host-gateway"

  mock-oauth2:
    image: ghcr.io/navikt/mock-oauth2-server:2.1.10
    ports:
      - "8888:8888"
```

The base `docker-compose.yml` should be a complete, working configuration for production/deployment (without port mappings when behind a reverse proxy). The override file adds only what is needed for local development.

See [application-skeleton](https://github.com/OpenElementsLabs/application-skeleton) for a working example of this pattern with a mock OAuth2 server.

### Common Docker Compose Commands

Document the following commands in the project README:

- **Start with rebuild**: `docker-compose up --build` — Always use `--build` to ensure code changes are reflected in the containers. Without this flag, Docker Compose reuses cached images and changes are not visible.
- **Stop**: `docker-compose down` — Stops and removes containers.
- **Stop and remove volumes**: `docker-compose down -v` — Also removes persistent data (databases, etc.).

## Communication Between Frontend and Backend

- The frontend communicates with the backend exclusively through HTTP APIs.
- **IMPORTANT**: The frontend application must never call the backend directly from the browser. Instead, route all API calls through the frontend's server-side layer. This avoids CORS issues and prevents exposing internal backend URLs to the client. Note: the backend's Swagger UI is accessed directly by developers for API exploration — this rule applies to the frontend application's API communication only.
- **For Next.js**: Use [Next.js Rewrites](https://nextjs.org/docs/app/api-reference/config/next-config-js/rewrites) in `next.config.ts` to proxy API requests to the backend. **IMPORTANT**: The rewrite destination must use the `BACKEND_URL` environment variable — never hardcode `localhost`. In Docker Compose, `BACKEND_URL` is `http://backend:8080` (the Docker service name). In local development, it is `http://localhost:8080` (or whatever port the backend runs on).

  Example `next.config.ts` rewrite:
  ```typescript
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL}/api/:path*`,
      },
    ];
  }
  ```

  With this setup, frontend code fetches from its own origin (e.g., `fetch('/api/status')`) and Next.js proxies the request server-side to the backend.
- In Docker Compose, the frontend server-side proxy reaches the backend via the Docker service name (e.g., `http://backend:8080`). The browser only communicates with the frontend container.
- In local development, the frontend proxy connects to the backend via `localhost` and the backend's dev port. Set `BACKEND_URL=http://localhost:8080` in the frontend's `.env` or start script.
- Do not configure CORS on the backend to allow frontend origins as a workaround — use the proxy approach instead.
- API contracts should be clearly defined. Changes to the API should be coordinated between frontend and backend.

## Authentication with OAuth2/OIDC

Fullstack applications that require user authentication should use **OAuth2/OIDC** with a generic provider configuration. This allows the same codebase to work with any OIDC-compliant identity provider (Authentik, Keycloak, Auth0, etc.).

### Architecture

- **Backend**: Configure as an OAuth2 Resource Server that validates JWTs. Use `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (Spring Boot) to validate tokens against the identity provider.
- **Frontend**: Use [NextAuth.js (Auth.js)](https://authjs.dev/) with a generic OIDC provider. Use JWT session strategy (no database sessions). Implement token refresh flow.
- **Middleware**: Protect all routes except public pages (login, health checks, static assets) via Next.js middleware.

### Required Environment Variables

Configure the identity provider entirely via environment variables — never hardcode provider URLs or credentials:

- `OIDC_ISSUER_URI` — The OIDC issuer URL (e.g., `https://auth.example.com/application/o/my-app/`)
- `OIDC_CLIENT_ID` — OAuth2 client ID
- `OIDC_CLIENT_SECRET` — OAuth2 client secret
- `OIDC_JWK_SET_URI` — JWK Set URI for token validation (backend)
- `AUTH_SECRET` — NextAuth.js session encryption secret (frontend)
- `AUTH_TRUST_HOST=true` — Required when the frontend runs behind a reverse proxy

### Local Development with Mock OAuth2

For local development, use a mock OAuth2 server (e.g., `ghcr.io/navikt/mock-oauth2-server`) in `docker-compose.override.yml` instead of requiring a real identity provider. Override the backend's `OIDC_JWK_SET_URI` to point to the mock server.

See [application-skeleton](https://github.com/OpenElementsLabs/application-skeleton) for a complete working example.

### Deployment with Authentik on Coolify

For Open Elements projects deployed on Coolify, the identity provider is **Authentik**. Configure an OAuth2/OpenID Provider in Authentik with:

- Redirect URI pointing to the frontend's NextAuth callback (`https://<frontend-url>/api/auth/callback/oidc`)
- Scopes: `openid profile email offline_access`
- Set the environment variables (`OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, etc.) in the Coolify service configuration.

## Pinned Tool Versions

Pin exact versions of runtimes and build tools in the repository so that every developer and CI environment uses the same versions.

- **Java**: Use a `.sdkmanrc` file in the backend directory to pin the Java version (e.g., `java=21`). Developers activate it with `sdk env install`. The pinned version must be compatible with the framework in use — check the framework's supported Java versions.
- **Node.js**: Use a `.nvmrc` file in the frontend directory to pin the Node.js version (e.g., `v22.19.0`). Developers activate it with `nvm install`.
- **Build tool wrappers**: Use the Maven Wrapper (`mvnw`) or Gradle Wrapper (`gradlew`) so the build tool version is committed to the repository and does not need to be installed separately.
- Do not rely on globally installed tool versions. The repository must define everything needed to build and run.

## Configuration

- **IMPORTANT**: Both frontend and backend must be configurable via environment variables. All environment-specific values (database URLs, API keys, feature flags, external service URLs) must be read from environment variables — never hardcoded.
- For local development, use a `.env` file at the repository root (or per application directory) to define environment variables. Docker Compose loads `.env` files automatically.
- Add `.env` to `.gitignore`. Provide a `.env.example` file with all required variables and sensible defaults or placeholder values as documentation.
- When setting up a project, copy `.env.example` to `.env` if no `.env` file exists yet. This ensures the project is immediately runnable. Document this step in the README.
- In hosted environments (Coolify, cloud platforms, CI/CD), set environment variables directly in the platform configuration instead of using `.env` files.
- Design configuration so that the same container image can run in any environment (local, test, production) — only the environment variables change.

## What to Avoid

- **IMPORTANT**: Do not share code between frontend and backend (no shared `lib/` or common modules).
- Do not create a single Dockerfile that builds both applications.
- Do not use monorepo tools (Nx, Turborepo) to couple the build processes.
- **IMPORTANT**: Do not hardcode ports or URLs — use environment variables with sensible defaults.
- **IMPORTANT**: Never hardcode credentials (passwords, usernames, API keys) directly in `docker-compose.yml` or any other checked-in file. Use environment variable references (`${DB_PASSWORD}`) and define the values in `.env` (which is gitignored). The `.env.example` file should contain only placeholder values (e.g., `DB_PASSWORD=changeme`).
