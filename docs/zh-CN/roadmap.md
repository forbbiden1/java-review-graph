# 路线图

## 里程碑 0

- 初始化仓库结构
- 建立模块边界
- 补齐 schema 和 API 草案
- 加入最小可启动的 server
- 对齐本地 SQLite 存储层

## 里程碑 1

- 导入一个 Maven Java 项目
- 发现源码根目录和模块
- 解析 JDT classpath
- 解析类型和方法

## 里程碑 2

- 抽取 `extends`、`implements`、`uses_type`、`calls` 和 `overrides`
- 持久化快照
- 提供类图查询 API

## 里程碑 3

- 支持基于变更文件的增量重建
- 计算快照 diff
- 标记 changed 和 impacted 符号

## 里程碑 4

- 在前端渲染类图
- 展开选中类的方法节点
- 高亮 changed 和 impacted 节点
