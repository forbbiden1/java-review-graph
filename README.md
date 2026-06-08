# Java Review Graph

Java Review Graph is a local-first monorepo for building a review-oriented code graph for Java projects.
The product goal is to visualize type relationships by default, expand methods on demand, and highlight changed or impacted symbols after each edit.
The current storage layer is SQLite, with a local database file under `data/`.

## Repository Layout

```text
apps/
  analyzer-jdt/   Java analysis pipeline and incremental graph extraction
  desktop/        Electron shell that wraps the review UI and desktop settings
  server/         Spring Boot API for indexing, graph queries, and review views
  web/            React UI shared by browser and desktop renderers
libs/
  model/          Shared Java graph, review, and analysis models
docs/             Architecture, schema, API, and development notes
examples/         Example payloads and fixture notes
```

## Documentation

Development documents are organized from architecture down to module details.
Start with [docs/README.md](./docs/README.md) for the reading order and document roles.
Chinese documentation is available at [docs/zh-CN/README.md](./docs/zh-CN/README.md).

## MVP Scope

- Import a local Java project
- Build a type graph with `extends`, `implements`, `uses`, and `calls`
- Mark changed and impacted types or methods after each incremental re-index
- Show a class-level graph first, then expand methods for a selected type

## Current Status

This repository now includes:

- a Maven multi-module Java workspace
- a Spring Boot server skeleton
- a SQLite-backed local storage baseline for graph metadata
- a local AST-based analyzer baseline aligned for Eclipse JDT evolution
- a React review UI with a scatter-style graph canvas
- an Electron desktop workspace with persisted settings and bilingual chrome
- baseline design documentation

## Next Steps

1. Add impacted-symbol propagation across classes and methods
2. Launch the local Spring API automatically from the desktop shell
3. Extend method graphs beyond same-class `calls`
4. Add snapshot diff summaries and richer review annotations
