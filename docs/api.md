# API Draft

## Health

`GET /api/health`

Returns a basic liveness response for the server.

## Bootstrap Overview

`GET /api/projects/bootstrap`

Returns the initial module overview for the local project skeleton.

## Planned Endpoints

### Import a project

`POST /api/projects`

Request:

```json
{
  "name": "demo-project",
  "rootPath": "C:/repo/demo-project"
}
```

Current behavior:

- validates that `rootPath` exists and is a directory
- rejects non-Java repositories and does not persist them
- detects `maven`, `gradle`, or `unknown`
- returns `201 Created` for a new project
- returns `200 OK` with the existing record if the same normalized root path was already imported

### List imported projects

`GET /api/projects`

### Query one project

`GET /api/projects/{projectId}`

### Delete one project

`DELETE /api/projects/{projectId}`

Current behavior:

- removes the project record
- removes snapshots and persisted review data for that project
- returns `204 No Content` when deletion succeeds

### Trigger indexing

`POST /api/projects/{projectId}/index`

Request:

```json
{
  "mode": "full"
}
```

or

```json
{
  "mode": "incremental",
  "changeSource": "git"
}
```

or

```json
{
  "mode": "incremental",
  "changeSource": "manual",
  "changedFiles": [
    "src/main/java/com/example/user/UserService.java"
  ]
}
```

Current behavior:

- validates `mode` as `full` or `incremental`
- supports `changeSource = git` or `manual` for `incremental`
- defaults `changeSource` to `git` when omitted
- requires `changedFiles` only for `manual` incremental requests
- uses the latest snapshot as the incremental base when one exists
- when `changeSource = git`, collects changed paths from the latest snapshot commit to the current workspace state
- also includes current uncommitted and untracked Git changes in automatic incremental mode
- normalizes changed Java paths and expands the rebuild set with one-hop related files from the previous snapshot
- detects Git rename or move pairs and persists them explicitly in snapshot diagnostics
- falls back to a full scan when no previous snapshot exists or build metadata changed
- reuses previous snapshot data unchanged when an incremental request contains no Java source changes
- creates a persisted snapshot record
- persists snapshot diagnostics for the requested mode, effective mode, change source, changed files, rebuild scope, and fallback reason
- sets `displayName` to the snapshot UUID by default
- when the project root is a clean Git work tree, stores the current `gitCommit` and `gitCommitMessage`
- when the work tree has uncommitted changes or Git metadata is unavailable, stores `gitCommit = null` and `gitCommitMessage = null`
- runs the local AST analyzer synchronously for the rebuild set or full fallback scan
- persists the assembled full `source_file`, `symbol`, `relation`, and `symbol_change` snapshot into SQLite
- returns snapshot metadata and the current analysis summary

### Query project snapshots

`GET /api/projects/{projectId}/snapshots`

Current behavior:

- returns snapshots ordered by `createdAt desc`
- each snapshot includes `gitCommit`, `gitCommitMessage`, and `displayName`
- uncommitted or non-Git snapshots return those two fields as `null`

### Query snapshot diagnostics

`GET /api/projects/{projectId}/snapshots/{snapshotId}/diagnostics`

Current behavior:

- returns the persisted diagnostics metadata for the selected snapshot
- includes `requestedMode`, `effectiveMode`, and `changeSource`
- includes `changedFiles`, `renamedPaths`, `rebuildPaths`, and `removedPaths`
- includes `note`, `fallbackReason`, and `includesWorkspaceChanges`

### Rename one snapshot

`PATCH /api/projects/{projectId}/snapshots/{snapshotId}`

Request:

```json
{
  "displayName": "Review Baseline"
}
```

Current behavior:

- keeps the snapshot `id` unchanged
- updates only the user-visible `displayName`
- rejects blank names

### Delete one snapshot

`DELETE /api/projects/{projectId}/snapshots/{snapshotId}`

Current behavior:

- removes the snapshot record
- removes the persisted `source_file`, `symbol`, `relation`, and `symbol_change` rows for that snapshot only
- clears `base_snapshot_id` on same-project descendants that pointed at the deleted snapshot
- returns `204 No Content` when deletion succeeds

### Query class graph

`GET /api/projects/{projectId}/graph/classes?snapshotId={snapshotId}&onlyChanged=true`

Current behavior:

- returns persisted type nodes from the selected snapshot
- returns `extends`, `implements`, and `uses_type` edges between in-project types
- each node now includes `layer`, `order`, `group`, `groupOrder`, and `placement` for condensed-SCC layered rendering
- resolves `snapshotId` to the latest snapshot when omitted

### Query type method graph

`GET /api/projects/{projectId}/method-graph?classId={classId}&snapshotId={snapshotId}`

Current behavior:

- returns method nodes declared by the selected class
- returns locally resolved `calls` edges between methods in the same class
- each node now includes `layer`, `order`, `group`, `groupOrder`, and `placement` for condensed-SCC layered rendering
- keeps legacy slash-containing symbol keys usable because `classId` is passed as a query parameter

### Query changed symbols

`GET /api/projects/{projectId}/changes?snapshotId={snapshotId}`

Current behavior:

- returns persisted `added`, `modified_api`, `modified_impl`, `impacted`, and `deleted` entries for the selected snapshot
- `impacted` is derived with one-hop propagation from changed or deleted neighbors

### Review one change set

`POST /api/projects/{projectId}/review/change-set`

Request:

```json
{
  "snapshotId": "snapshot-1",
  "changeSource": "manual",
  "changedFiles": [
    "src/main/java/demo/Service.java"
  ]
}
```

or:

```json
{
  "snapshotId": "snapshot-1",
  "changeSource": "git"
}
```

Current behavior:

- resolves `snapshotId` to the latest snapshot when omitted
- supports `changeSource = git` or `manual`
- defaults `changeSource` to `git` when omitted
- requires `changedFiles` only for `manual` review requests
- when `changeSource = git`, reuses the snapshot Git base to collect changed paths from Git
- maps changed file paths to changed symbols in the selected snapshot
- reads persisted symbol-change rows to surface impacted or deleted symbols for review
- returns a compact summary including changed files, rename pairs, changed symbols, impacted symbols, prioritized review targets, direct propagation paths, a deterministic risk level, and one summary sentence
- also returns deterministic test-focus suggestions derived from risk and propagation evidence

### Export change-set review Markdown

`POST /api/projects/{projectId}/review/change-set/markdown`

Request body is the same as `POST /api/projects/{projectId}/review/change-set`.

Current behavior:

- runs the same deterministic change-set review flow against one selected snapshot
- returns an export-ready Markdown body plus a suggested file name
- includes scope, summary, deterministic risk reasons, changed files, renamed paths, prioritized review targets, direct propagation paths, changed symbols, and impacted symbols
- includes deterministic test-focus suggestions derived from risk and propagation evidence
