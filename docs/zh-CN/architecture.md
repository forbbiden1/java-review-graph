# 架构设计

## 目标

Java Review Graph 是一个本地优先的代码 review 工具。
它会为 Java 项目构建符号关系图，默认展示类型级关系；当审查者选择某个类型时，再展开该类型内部的方法级关系和变更信息。

## 核心流程

```text
Java 仓库
  -> 项目模型解析
  -> JDT AST 解析与绑定解析
  -> 符号与关系抽取
  -> 快照 diff 与影响传播
  -> 查询 API
  -> 图谱 review 界面
```

## 模块划分

### `libs/model`

共享记录和枚举，覆盖：

- 符号
- 关系
- 快照
- review 状态

### `apps/analyzer-jdt`

规划职责：

- 识别源码根目录和模块
- 从 Maven 或 Gradle 结构解析 classpath
- 使用 JDT 构建带绑定信息的 AST
- 抽取类型和方法符号
- 构建关系边
- 计算 API 和实现 hash
- 对快照做 diff 并推导受影响符号

### `apps/server`

规划职责：

- 管理项目和快照
- 触发全量或增量索引
- 使用本地 SQLite 数据库存储图谱数据
- 提供图查询和 review API

### 存储选型

当前存储层选择 SQLite。
MVP 阶段图谱默认保存在 `data/java-review-graph.db` 这个本地数据库文件中。
这样部署成本最低，也符合当前单机单用户 review 工作流。

### `apps/web`

规划职责：

- 默认展示类图
- 在选中类型后展开该类内部的方法
- 展示变更状态和影响提示
- 提供聚焦的 review 侧边面板

## MVP 边界

第一阶段明确不做：

- 字段级图谱
- 运行时链路追踪
- 跨仓库图拼接
- Spring Bean 等框架级隐式依赖解析
- 多人协作或共享状态
