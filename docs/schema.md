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

## Symbol Key Rules

- type key: `type:{module}:{qualifiedTypeName}`
- method key: `method:{module}:{qualifiedTypeName}#{methodName}({erasedParamTypes})`
