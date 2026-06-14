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

当前面向查询的索引覆盖：

- 按项目和时间倒序查询快照
- 按项目和基线快照关系查询快照
- 按项目、快照和路径查询源文件
- 按项目、快照、类型、symbol key 和父 symbol 查询符号
- 按项目、快照、关系类型以及源/目标符号 key 查询关系

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

## 当前查询索引方向

- `snapshot(project_id, created_at desc)` 支持最新快照查询和快照列表视图
- `snapshot(project_id, base_snapshot_id)` 支持删除快照时维护增量基线链路
- `source_file(project_id, snapshot_id, path)` 支持增量快照组装和文件复用查询
- `symbol(project_id, snapshot_id, symbol_type)` 支持类节点查询
- `symbol(project_id, snapshot_id, parent_symbol_key)` 支持单类方法节点查询
- `symbol(project_id, snapshot_id, symbol_key)` 支持快照内稳定的 symbol key 定位
- `relation(project_id, snapshot_id, relation_type)` 支持类图关系切片查询
- `relation(project_id, snapshot_id, relation_type, source_symbol_key, target_symbol_key)` 支持按单类方法集合约束的方法调用子图读取

## Symbol Key 规则

- 类型 key：`type:{module}:{qualifiedTypeName}`
- 方法 key：`method:{module}:{qualifiedTypeName}#{methodName}({erasedParamTypes})`
