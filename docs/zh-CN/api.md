# API 草案

## 健康检查

`GET /api/health`

返回服务的基础存活信息。

## 初始化总览

`GET /api/projects/bootstrap`

返回当前项目骨架的初始模块总览。

## 规划中的接口

### 导入项目

`POST /api/projects`

请求：

```json
{
  "name": "demo-project",
  "rootPath": "C:/repo/demo-project"
}
```

当前行为：

- 校验 `rootPath` 是否存在且是目录
- 拒绝非 Java 项目，并且不保存到项目列表
- 自动识别 `maven`、`gradle` 或 `unknown`
- 新项目返回 `201 Created`
- 如果同一个规范化后的根路径已经导入过，则返回已有记录和 `200 OK`

### 查询已导入项目列表

`GET /api/projects`

### 查询单个项目

`GET /api/projects/{projectId}`

### 删除单个项目

`DELETE /api/projects/{projectId}`

当前行为：

- 删除项目记录
- 删除该项目下的快照和持久化 review 数据
- 成功时返回 `204 No Content`

### 触发索引

`POST /api/projects/{projectId}/index`

请求：

```json
{
  "mode": "full"
}
```

或者：

```json
{
  "mode": "incremental",
  "changeSource": "git"
}
```

或者：

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

- 校验 `mode` 只能是 `full` 或 `incremental`
- `incremental` 支持 `changeSource = git` 或 `manual`
- 未显式传入时，`changeSource` 默认按 `git` 处理
- 只有 `manual` 增量模式才要求提供 `changedFiles`
- 当存在历史快照时，会把最新快照当作增量基线
- 当 `changeSource = git` 时，会从最新快照对应的 commit 对比到当前工作区，自动收集变更路径
- 自动增量模式还会把当前未提交改动和未跟踪文件一起纳入变更集合
- 会规范化变更的 Java 路径，并把上一份快照中一跳关联的文件加入重建集合
- 会显式识别 Git rename / move 对，并把它们持久化到快照诊断信息里
- 如果没有上一份快照，或检测到构建元数据变化，会回退到全量扫描
- 如果增量请求里没有 Java 源码变更，则直接复用上一份快照数据
- 创建并持久化一条快照记录
- 会持久化快照诊断信息，包括请求模式、实际模式、变更来源、变更文件、重建范围和回退原因
- 新快照的 `displayName` 默认等于快照 UUID
- 当项目根目录处于干净 Git 工作树时，保存当前 `gitCommit` 和 `gitCommitMessage`
- 当存在未提交改动或无法读取 Git 信息时，保存 `gitCommit = null` 和 `gitCommitMessage = null`
- 对重建集合或全量回退结果同步执行本地 AST 分析
- 将组装后的完整 `source_file`、`symbol`、`relation` 和 `symbol_change` 快照写入 SQLite
- 返回快照元数据和当前分析摘要

### 查询项目快照列表

`GET /api/projects/{projectId}/snapshots`

当前行为：

- 按 `createdAt desc` 返回快照列表
- 每个快照都包含 `gitCommit`、`gitCommitMessage` 和 `displayName`
- 未提交或非 Git 场景下，前两个字段返回 `null`

### 查询快照诊断信息

`GET /api/projects/{projectId}/snapshots/{snapshotId}/diagnostics`

当前行为：

- 返回所选快照持久化保存的诊断元数据
- 包括 `requestedMode`、`effectiveMode` 和 `changeSource`
- 包括 `changedFiles`、`renamedPaths`、`rebuildPaths` 和 `removedPaths`
- 包括 `note`、`fallbackReason` 和 `includesWorkspaceChanges`

### 重命名单个快照

`PATCH /api/projects/{projectId}/snapshots/{snapshotId}`

请求：

```json
{
  "displayName": "Review Baseline"
}
```

当前行为：

- 保持快照 `id` 不变
- 仅更新用于展示的 `displayName`
- 空白名称会被拒绝

### 删除单个快照

`DELETE /api/projects/{projectId}/snapshots/{snapshotId}`

当前行为：

- 删除该快照记录
- 只删除该快照下的 `source_file`、`symbol`、`relation` 和 `symbol_change` 数据
- 如果同项目下其他快照的 `base_snapshot_id` 指向被删除快照，会被清成 `null`
- 成功时返回 `204 No Content`

### 查询类图

`GET /api/projects/{projectId}/graph/classes?snapshotId={snapshotId}&onlyChanged=true`

当前行为：

- 返回所选快照中的类型节点
- 返回项目内部类型之间的 `extends`、`implements` 和 `uses_type` 边
- 每个节点都包含 `layer`、`order`、`group`、`groupOrder` 和 `placement`，供前端按缩点后的分层结果渲染
- 当未传 `snapshotId` 时，默认解析到最新快照

### 查询类内方法图

`GET /api/projects/{projectId}/method-graph?classId={classId}&snapshotId={snapshotId}`

当前行为：

- 返回所选类下声明的方法节点
- 返回同一个类内部当前可解析到的 `calls` 边
- 每个节点都包含 `layer`、`order`、`group`、`groupOrder` 和 `placement`，供前端按缩点后的分层结果渲染
- `classId` 通过查询参数传递，因此历史快照里带 `/` 的 symbol key 也能正常展开

### 查询变更符号

`GET /api/projects/{projectId}/changes?snapshotId={snapshotId}`

当前行为：

- 返回所选快照中的 `added`、`modified_api`、`modified_impl`、`impacted` 和 `deleted` 记录
- `impacted` 会基于变更或删除邻居做一跳传播计算

### 分析一个变更集

`POST /api/projects/{projectId}/review/change-set`

请求：

```json
{
  "snapshotId": "snapshot-1",
  "changeSource": "manual",
  "changedFiles": [
    "src/main/java/demo/Service.java"
  ]
}
```

或者：

```json
{
  "snapshotId": "snapshot-1",
  "changeSource": "git"
}
```

当前行为：

- 未传 `snapshotId` 时，默认解析到最新快照
- 支持 `changeSource = git` 或 `manual`
- 未显式传入时，`changeSource` 默认按 `git` 处理
- 只有 `manual` 方式才要求提供 `changedFiles`
- 当 `changeSource = git` 时，会复用快照对应的 Git 基线自动收集变更路径
- 会把变更文件路径映射到所选快照中的变更符号
- 会读取已持久化的 `symbol_change` 记录，补充 review 所需的受影响符号或删除符号
- 返回紧凑摘要，包括变更文件、rename 对、变更符号、受影响符号、优先 review 目标、直接传播路径、确定性的风险等级以及一段汇总说明
- 还会基于风险和传播证据返回确定性的测试关注建议

### 导出 change-set review Markdown

`POST /api/projects/{projectId}/review/change-set/markdown`

请求体与 `POST /api/projects/{projectId}/review/change-set` 相同。

当前行为：

- 会基于同一个选定快照执行相同的确定性 change-set review 流程
- 返回可直接导出的 Markdown 正文以及建议文件名
- 内容包含 review 范围、摘要、确定性的风险原因、变更文件、rename 路径、优先 review 目标、直接传播路径、变更符号和受影响符号
- 也会包含基于风险和传播证据生成的确定性测试关注建议
