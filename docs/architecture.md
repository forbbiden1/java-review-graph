# Architecture

## Goal

Java Review Graph is a local-first review tool that builds a graph of Java project symbols and relationships.
The default experience is a type graph. A reviewer can then expand a selected type to inspect method-level relationships and changes.

## Core Flow

```text
Java repository
  -> project model resolution
  -> JDT AST parsing and binding resolution
  -> symbol and relation extraction
  -> snapshot diff and impact propagation
  -> query API
  -> graph review UI
```

## Modules

### `libs/model`

Shared records and enums for:

- symbols
- relations
- snapshots
- review status

### `apps/analyzer-jdt`

Planned responsibilities:

- detect source roots and modules
- resolve classpath from Maven or Gradle
- build JDT ASTs with bindings
- extract type and method symbols
- build relation edges
- compute API and implementation hashes
- diff snapshots and derive impacted symbols

### `apps/server`

Planned responsibilities:

- manage projects and snapshots
- trigger full or incremental indexing
- persist graph data in a local SQLite database
- expose graph and review APIs

### Storage choice

The current storage layer is SQLite.
For the MVP, the graph stays local-first and uses a single database file under `data/java-review-graph.db`.
This keeps setup cost low and fits the single-user review workflow.

### `apps/web`

Planned responsibilities:

- show class graph by default
- expand methods inside a selected type
- show change badges and impact hints
- provide a focused review panel

## MVP Boundaries

The first milestone intentionally excludes:

- field-level graphing
- runtime tracing
- cross-repository graph stitching
- framework-specific Spring bean resolution
- collaboration or multi-user state
