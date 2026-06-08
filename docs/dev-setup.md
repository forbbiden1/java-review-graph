# Development Setup

## Expected Tooling

- JDK 17
- Maven 3.9+
- Node.js 20+

## Backend

From the repository root:

```bash
mvn -pl apps/server -am spring-boot:run
```

This repository overrides the machine-level Maven settings through:

```text
.mvn/maven.config
.mvn/settings.xml
```

The goal is to force Maven to use the repo-local cache instead of the machine-wide
`D:\Environment\apache-maven-3.9.9\mvn_repo` path.

Health endpoint:

```text
http://localhost:8080/api/health
```

SQLite database file:

```text
./data/java-review-graph.db
```

Repo-local Maven repository:

```text
./.m2/repository
```

## Frontend

From `apps/web`:

```bash
npm install
npm run dev
```

Dev URL:

```text
http://localhost:5173
```

## Desktop

From `apps/desktop`:

```bash
npm install
npm run dev
```

The desktop renderer reuses the shared React UI from `apps/web` and persists settings
such as language and backend API base URL through Electron preload storage.

Default desktop backend URL:

```text
http://127.0.0.1:8080
```

## Short-Term Build Notes

- `apps/analyzer-jdt` already performs local AST extraction, but binding resolution and deeper semantic analysis are still not implemented
- SQLite is now wired for projects, snapshots, source files, symbols, relations, and symbol changes, but richer review queries are still incomplete
- the browser UI builds and uses the local Vite `/api` proxy when the API base URL is left blank
- the Electron desktop target builds separately and defaults to `http://127.0.0.1:8080` unless overridden in settings
