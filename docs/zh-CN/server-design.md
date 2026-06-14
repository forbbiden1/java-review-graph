# 服务端设计

## 目的

`apps/server` 是本地后端，负责项目导入、索引控制、快照持久化和图查询。当前持久化目标是本地 SQLite 数据库文件。

## 职责

- 管理项目元数据
- 触发全量和增量索引
- 持久化快照、符号、关系和变更记录
- 提供图查询和 review API
- 把分析器输出转换成适合查询的响应结果

## 分层

- `api`
  控制器以及请求或响应 DTO
- `application`
  用例编排和流程协调
- `domain`
  后端领域模型
- `infrastructure`
  持久化、文件访问和分析器集成适配

当前 `application` 层职责拆分：

- `ProjectIndexService`
  顶层索引用例编排
- `IncrementalPlanner`
  变更文件解析、增量回退规则和重建范围规划
- `SnapshotAssembler`
  组装重建结果与基线快照中的未变部分
- `ChangeStatusCalculator`
  计算符号变更状态与一跳受影响符号
- `ReviewQueryService`
  面向快照的类图、方法图与变更列表查询
- `ChangeSetReviewService`
  基于单个快照和 Git 自动、手工文件列表或明确提交区间生成确定性 review 摘要
- `SnapshotCompareService`
  基于两个持久化快照生成确定性的符号与关系演进摘要

## 主要用例

### 项目导入

- 注册本地仓库路径
- 检测构建工具
- 创建初始项目记录

### 全量索引

- 构建项目描述对象
- 调用分析器
- 持久化快照和图状态

### 增量索引

- 接收变更文件列表或 Git 自动 diff 输入
- 在存在历史快照时，以最新快照作为增量基线
- 支持 `changeSource = git|manual`
- 重建变化 Java 文件以及一跳相关文件
- 复用基线快照中未变化的文件、符号和关系
- 必要时回退到全量扫描

### 图查询与 review

- 类图
- 类内方法图
- 变更符号列表
- change-set review 摘要
- snapshot-to-snapshot compare 摘要

当前查询链路行为：

- 类图只读取所需的类型级关系
- 方法图只读取当前类范围内的调用关系
- 符号路径查询会在持久化的 `EXTENDS`、`IMPLEMENTS`、`USES_TYPE`、`CALLS`、`OVERRIDES` 关系上执行有界 BFS，用于解释两个 review 相关符号之间的多跳追踪路径
- change-set review 基于单快照和 Git 自动、手工文件列表或 `baseCommit -> targetCommit` 提交区间生成风险、目标、传播路径和测试建议
- 提交区间 review 只读取两端 commit 的已提交 diff，并保留 rename / move 路径，不会混入当前工作区未提交改动
- 风险摘要现在同时保留纯文本原因和结构化因子，结构化因子会记录规则代码、分值贡献、严重级别和证据
- UI 也可以在 review 结果之外再单独调用符号路径接口，展示一条有界的影响追踪链路，而不改变确定性的 review 返回体
- snapshot compare 直接读取两个快照中的持久化符号集和结构关系集，输出符号变化以及 `extends`、`implements`、`uses_type`、`calls`、`overrides` 等关系的新增与删除

## 持久化方向

后端需要存储：

- 项目和快照元数据
- 规范化后的 symbol 记录
- 规范化后的 relation 记录
- 显式的 symbol change 记录

后端不依赖前端图布局引擎。

## SQLite 决策

- 数据库引擎：SQLite
- 默认数据库路径：`./data/java-review-graph.db`
- 初始化方式：Spring 启动时执行 SQL 初始化
- 当前访问方式：基于 JDBC 的本地持久化

## MVP 规则

API 响应应围绕 review 任务组织，而不是直接暴露底层存储结构。优先提供 changed-only 视图、局部方法展开，以及适合演示的确定性差异摘要。
