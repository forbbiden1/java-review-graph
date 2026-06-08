# 存储模型草案

## 设计原则

- 跨快照保持符号身份稳定
- 将当前图谱存储和变更跟踪分离
- 支持不加载全部方法边的类级查询
- 支持按快照直接查询 review 状态

## 当前存储引擎

当前存储引擎为 SQLite。
运行时 schema 初始化文件位于 `apps/server/src/main/resources/db/sqlite/schema.sql`。
`docs/schema.sql` 作为人类可读的参考 schema，需要与运行时 SQLite schema 保持一致。

## 核心表

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

## Symbol Key 规则

- 类型 key：`type:{module}:{qualifiedTypeName}`
- 方法 key：`method:{module}:{qualifiedTypeName}#{methodName}({erasedParamTypes})`
