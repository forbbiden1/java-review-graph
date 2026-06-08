# 中文文档索引

这里是 Java Review Graph 的中文开发文档入口。
建议按下面的顺序阅读，先建立整体认知，再进入模块实现。

## 阅读顺序

1. [architecture.md](./architecture.md)
   系统目标、模块边界和 MVP 范围。
2. [review-model.md](./review-model.md)
   面向 review 的产品模型，包括符号层级、关系类型、变更状态和界面关注点。
3. [indexing-flow.md](./indexing-flow.md)
   全量索引、增量索引、快照 diff 和影响传播流程。
4. [analyzer-design.md](./analyzer-design.md)
   Java 分析器职责、JDT 流程和抽取阶段。
5. [server-design.md](./server-design.md)
   后端职责、持久化方向和 API 分层。
6. [web-design.md](./web-design.md)
   前端页面结构和图交互规则。
7. [api.md](./api.md)
   对外 API 草案。
8. [schema.md](./schema.md)
   存储模型和符号稳定标识规则。
9. [../schema.sql](../schema.sql)
   初版 SQL 草案，SQL 本身不再单独翻译。
10. [dev-setup.md](./dev-setup.md)
    本地开发环境说明和当前构建注意事项。
11. [roadmap.md](./roadmap.md)
    里程碑顺序。

## 文档分工

- 产品和架构：
  `architecture.md`、`review-model.md`
- 分析器和索引：
  `analyzer-design.md`、`indexing-flow.md`
- 后端和存储：
  `server-design.md`、`api.md`、`schema.md`、`../schema.sql`
- 前端：
  `web-design.md`
- 开发辅助：
  `dev-setup.md`、`roadmap.md`

## 维护规则

如果出现下面这些变化，先更新对应文档，再改代码：

- 模块边界变化
- 符号模型变化
- 索引流程变化
- API 合同变化
- review 交互变化

## 对照入口

- 英文文档入口：[../README.md](../README.md)
- 根目录说明：[../../README.md](../../README.md)
