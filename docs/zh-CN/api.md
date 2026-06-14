# API 设计

## `POST /api/projects`

导入一个本地 Java 项目。

请求体：

```json
{
  "name": "java-review-graph",
  "rootPath": "C:/repo/java-review-graph"
}
```

当前行为：

- 校验 `name` 和 `rootPath`
- 若 `rootPath` 已导入，则返回现有项目
- 自动识别构建工具

## `GET /api/projects`

返回所有已导入项目。

## `DELETE /api/projects/{projectId}`

删除一个项目及其快照、持久化 review 数据。

## `POST /api/projects/{projectId}/index`

为项目创建一次全量或增量快照。

请求体示例：

```json
{
  "mode": "full"
}
```

或：

```json
{
  "mode": "incremental",
  "changeSource": "git",
  "impactDepth": 2
}
```

或：

```json
{
  "mode": "incremental",
  "changeSource": "manual",
  "changedFiles": [
    "src/main/java/com/example/user/UserService.java"
  ]
}
```

当前行为：

- `mode` 只支持 `full` 和 `incremental`
- `incremental` 支持 `changeSource = git` 或 `manual`
- 未显式传入 `changeSource` 时默认按 `git` 处理
- `manual` 增量模式要求提供 `changedFiles`
- `impactDepth` 可选，默认是 `1`，并限制在 `1..4`
- 自动增量模式会基于最新快照对应的 Git 基线和当前工作区收集变更文件
- 会持久化快照、诊断信息以及完整 `source_file`、`symbol`、`relation`、`symbol_change`

## `GET /api/projects/{projectId}/snapshots`

查询项目快照列表。

当前行为：

- 按 `createdAt desc` 返回
- 每个快照包含 `gitCommit`、`gitCommitMessage` 和 `displayName`

## `GET /api/projects/{projectId}/snapshots/{snapshotId}/diagnostics`

查询单个快照的诊断信息。

当前行为：

- 返回 `requestedMode`、`effectiveMode`、`changeSource`
- 返回 `changedFiles`、`renamedPaths`、`rebuildPaths`、`removedPaths`
- 返回 `note`、`fallbackReason`、`includesWorkspaceChanges`

## `GET /api/projects/{projectId}/snapshots/compare?baseSnapshotId={baseSnapshotId}&targetSnapshotId={targetSnapshotId}`

对比两个持久化快照的符号和结构关系差异。

当前行为：

- 必须显式传入 `baseSnapshotId` 和 `targetSnapshotId`
- 不允许用同一个快照 id 同时作为基线和目标
- 按 `symbolKey` 对比两个快照中的持久化符号
- 基于符号存在性以及 `apiHash` / `implHash`，确定性产出 `added`、`deleted`、`modified_api`、`modified_impl`
- 还会按 `source_symbol_key`、`target_symbol_key`、`relation_type` 对比持久化结构关系
- 当前结构关系对比范围包括 `extends`、`implements`、`uses_type`、`calls`、`overrides`，不包含仅用于声明归属的边
- 返回符号摘要、逐符号差异、关系摘要、逐关系差异，供前端直接展示

## `PATCH /api/projects/{projectId}/snapshots/{snapshotId}`

重命名一个快照。

请求体：

```json
{
  "displayName": "Review Baseline"
}
```

当前行为：

- 保持 `id` 不变
- 仅更新展示用的 `displayName`
- 拒绝空白名称

## `DELETE /api/projects/{projectId}/snapshots/{snapshotId}`

删除一个快照。

当前行为：

- 删除该快照记录
- 删除该快照下的 `source_file`、`symbol`、`relation`、`symbol_change`
- 清空同项目后继快照里指向它的 `base_snapshot_id`
- 成功时返回 `204 No Content`

## `GET /api/projects/{projectId}/graph/classes?snapshotId={snapshotId}`

查询类图。

当前行为：

- 返回所选快照中的类型节点
- 返回项目内 `extends`、`implements`、`uses_type` 边
- 未传 `snapshotId` 时默认解析到最新快照

## `GET /api/projects/{projectId}/method-graph?classId={classId}&snapshotId={snapshotId}`

查询类内方法图。

当前行为：

- 返回所选类中的方法节点
- 返回当前可解析到的同类内部 `calls` 边

## `GET /api/projects/{projectId}/changes?snapshotId={snapshotId}`

查询指定快照的变更符号。

当前行为：

- 返回 `added`、`modified_api`、`modified_impl`、`impacted`、`deleted`
- `impacted` 基于可配置传播深度计算，默认仍是一跳

## `GET /api/projects/{projectId}/symbols/path?snapshotId={snapshotId}&sourceSymbolKey={sourceSymbolKey}&targetSymbolKey={targetSymbolKey}&maxDepth=4`

查询两个符号之间的依赖或调用路径，用于 review 影响追踪。

当前行为：

- 未传 `snapshotId` 时默认解析到最新快照
- 必须传入 `sourceSymbolKey` 和 `targetSymbolKey`
- `maxDepth` 限制在 `1` 到 `8` 之间，默认值为 `4`
- 在持久化的 `extends`、`implements`、`uses_type`、`calls`、`overrides` 关系上执行有界最短路径搜索
- 返回按追踪顺序排列的符号节点和关系片段
- API 响应中的 `relationType` 使用小写字符串，便于前端统一展示
- 当符号不存在或深度范围内没有路径时，返回 `found = false` 和解释性的 `note`

## `POST /api/projects/{projectId}/review/change-set`

对一个快照执行 change-set review。

请求体示例：

```json
{
  "snapshotId": "snapshot-1",
  "changeSource": "manual",
  "changedFiles": [
    "src/main/java/demo/Service.java"
  ]
}
```

或：

```json
{
  "snapshotId": "snapshot-1",
  "changeSource": "git",
  "baseCommit": "abc12345",
  "targetCommit": "def67890"
}
```

当前行为：

- 未传 `snapshotId` 时默认使用最新快照
- 支持 `changeSource = git` 或 `manual`
- 支持同时传入 `baseCommit` 和 `targetCommit` 来执行明确的 Git 提交区间 review
- 提交区间模式会拒绝只传一端 commit，且不能与 `manual` 手工文件列表混用
- 提交区间模式基于两端 commit 的 `git diff --name-status --find-renames` 收集变更文件和重命名路径，不会带入当前工作区未提交改动
- 返回变更符号、受影响符号、优先 review 目标、传播路径、测试关注建议和确定性风险摘要
- 风险结果同时保留兼容性的 `risk.reasons` 和结构化的 `risk.factors`，后者会给出规则代码、分值贡献、严重级别和证据文本

## `POST /api/projects/{projectId}/review/change-set/markdown`

导出 change-set review 的 Markdown 报告。

当前行为：

- 请求体与 `POST /api/projects/{projectId}/review/change-set` 相同
- 基于同一套确定性 review 结果生成 Markdown 正文和建议文件名，并带上结构化风险因子说明
