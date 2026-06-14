# Server Design

## Purpose

`apps/server` hosts the local backend for project import, indexing control, snapshot persistence, and graph queries.
The current persistence target is a local SQLite database file.

## Responsibilities

- manage project metadata
- trigger full and incremental indexing
- persist snapshots, symbols, relations, and changes
- expose graph and review APIs
- translate analyzer output into query-friendly payloads

## Layering

- `api`
  controllers and request or response DTOs
- `application`
  use cases and orchestration
- `domain`
  backend-specific domain models
- `infrastructure`
  persistence, file access, and analyzer integration adapters

Current application-layer split:

- `ProjectIndexService`
  top-level indexing use case orchestration
- `IncrementalPlanner`
  changed-file resolution, incremental fallback rules, and rebuild scope planning
- `SnapshotAssembler`
  merge rebuilt analyzer output with unchanged snapshot state
- `ChangeStatusCalculator`
  symbol diff status and impacted-symbol derivation
- `ReviewQueryService`
  snapshot-aware graph query assembly with targeted relation reads for class and method views
- `ChangeSetReviewService`
  review-oriented change-set summary assembly based on one snapshot and one Git or manual file set

## Main Use Cases

### Project import

- register local repository path
- detect build tool
- create initial project record

The first implemented server flow should cover:

- insert or reuse a project record in SQLite
- list imported projects
- query one imported project by id

### Full index

- build project descriptor
- call analyzer
- persist snapshot and graph state

### Incremental index

- accept changed files or Git-driven auto diff input
- use the latest snapshot as the incremental base when available
- support `changeSource = git|manual`, defaulting to `git`
- rebuild changed Java files plus one-hop related files from the previous snapshot
- reuse unchanged files, symbols, and relations from the base snapshot
- fall back to a full scan when build metadata changed or no base snapshot exists
- persist the assembled snapshot and change set
- compute one-hop impacted symbol summary

### Graph query

- class graph
- class detail
- method graph for one class
- changed symbol list
- impact scope
- change-set review summary

Current query-path behavior:

- class graph reads only `EXTENDS`, `IMPLEMENTS`, and `USES_TYPE` relations for the selected snapshot, then trims to indexed type-to-type edges
- method graph reads only `CALLS` relations whose source and target both belong to the selected class methods
- change-set review reads one snapshot plus one Git or manual changed-file set, then maps file paths to changed symbols and persisted impacted symbols
- change-set review also derives a deterministic risk summary from changed statuses, impacted count, and deleted-symbol signals
- query endpoints avoid loading the full snapshot relation set when a narrower relation slice is enough

## Persistence Direction

The backend should store:

- project and snapshot metadata
- normalized symbol records
- normalized relation records
- explicit symbol change records

The backend should not depend on the UI graph layout engine.

## SQLite Decision

- database engine: SQLite
- default database path: `./data/java-review-graph.db`
- initialization method: Spring SQL init on startup
- current access style: JDBC-oriented local persistence

SQLite is the default because the current product is local-first and single-user.
If multi-user or remote deployment appears later, the persistence abstraction can be revisited.

## MVP Rule

API responses should be optimized for review tasks, not raw storage dumps.
Prefer endpoints that directly support changed-only graph views and local method expansion.
