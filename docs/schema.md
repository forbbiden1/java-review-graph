# Schema Draft

## Design Principles

- keep symbol identity stable across snapshots
- separate current graph storage from change tracking
- allow class-level queries without loading all method edges
- keep review status queryable by snapshot

## Current Storage Engine

The current storage engine is SQLite.
The runtime schema is initialized from `apps/server/src/main/resources/db/sqlite/schema.sql`.
The `docs/schema.sql` file remains the human-readable schema reference and should stay aligned with the runtime SQLite schema.

Current query-oriented indexes cover:

- snapshots by project and newest-first ordering
- snapshots by project and base snapshot linkage
- source files by project, snapshot, and path
- symbols by project, snapshot, type, key, and parent symbol
- relations by project, snapshot, relation type, and source or target symbol keys

## Core Tables

### `project`

- `id`
- `name`
- `root_path`
- `build_tool`
- `created_at`
- `updated_at`

### `snapshot`

- `id`
- `project_id`
- `base_snapshot_id`
- `trigger_type`
- `git_commit`
- `git_commit_message`
- `display_name`
- `status`
- `requested_mode`
- `effective_mode`
- `change_source`
- `includes_workspace_changes`
- `diagnostics_note`
- `fallback_reason`
- `changed_files_json`
- `renamed_paths_json`
- `rebuild_paths_json`
- `removed_paths_json`
- `created_at`

### `source_file`

- `id`
- `project_id`
- `snapshot_id`
- `path`
- `module_name`
- `package_name`
- `content_hash`
- `scope`
- `updated_at`

### `symbol`

- `id`
- `project_id`
- `snapshot_id`
- `file_id`
- `symbol_key`
- `symbol_type`
- `parent_symbol_key`
- `qualified_name`
- `signature`
- `kind`
- `start_line`
- `end_line`
- `api_hash`
- `impl_hash`
- `change_status`
- `metadata_json`

### `relation`

- `id`
- `project_id`
- `snapshot_id`
- `source_symbol_key`
- `target_symbol_key`
- `relation_type`
- `confidence`
- `source_file_id`
- `source_line`
- `metadata_json`

### `symbol_change`

- `id`
- `project_id`
- `snapshot_id`
- `symbol_key`
- `before_symbol_id`
- `after_symbol_id`
- `change_type`
- `reason`

## Current Query Index Direction

- `snapshot(project_id, created_at desc)` supports latest-snapshot lookup and snapshot list views
- `snapshot(project_id, base_snapshot_id)` supports incremental lineage maintenance when deleting a snapshot
- `source_file(project_id, snapshot_id, path)` supports incremental snapshot merge and file reuse lookups
- `symbol(project_id, snapshot_id, symbol_type)` supports class-node queries
- `symbol(project_id, snapshot_id, parent_symbol_key)` supports method-node queries for one class
- `symbol(project_id, snapshot_id, symbol_key)` supports stable symbol-key lookup within a snapshot
- `relation(project_id, snapshot_id, relation_type)` supports class-graph relation slices
- `relation(project_id, snapshot_id, relation_type, source_symbol_key, target_symbol_key)` supports method-call subgraph reads constrained to one class

## Symbol Key Rules

- type key: `type:{module}:{qualifiedTypeName}`
- method key: `method:{module}:{qualifiedTypeName}#{methodName}({erasedParamTypes})`
