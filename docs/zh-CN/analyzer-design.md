# 分析器设计

## 目的

`apps/analyzer-jdt` 是 Java 分析引擎。
它负责把 Java 项目转换成符号、关系、hash，以及可用于 diff 的结构化数据。

## 核心职责

- 识别项目模块和源码根目录
- 从 Maven 或 Gradle 布局解析 classpath
- 使用 Eclipse JDT 解析 Java 源码
- 解析类型和方法绑定
- 抽取类型和方法符号
- 抽取关系边
- 计算稳定的 symbol key 和 hash

## 包职责

- `project`
  项目与模块发现
- `parser`
  JDT 解析器配置和 AST 批处理执行
- `extractor`
  符号与关系抽取
- `diff`
  快照对比
- `impact`
  变更邻居传播

## 规划中的流水线

1. `ProjectModelBuilder`
   识别模块、源码根目录和构建工具信息
2. `ClasspathResolver`
   为绑定解析准备二进制依赖和源码依赖
3. `AstBatchParser`
   调用 `ASTParser.createASTs(...)`
4. `TypeExtractor`
   收集 class、interface、enum、record、annotation 符号
5. `MethodExtractor`
   收集方法和构造器
6. `RelationExtractor`
   收集 `extends`、`implements`、`uses_type`、`calls` 和 `overrides`
7. `HashBuilder`
   生成 API 和实现 hash
8. `SnapshotDiffer`
   对比前后图谱状态
9. `ImpactAnalyzer`
   标记受影响符号

## 绑定解析预期

分析器应该优先使用精确的语义绑定。
如果绑定解析失败，关系仍然可以输出，但要降低置信度，并附带明确的回退元数据。

## 置信度等级

- `exact`
- `possible`
- `unresolved`

## 已知风险点

- Lombok 生成成员
- 反射驱动的类型加载
- classpath 不完整
- 多模块依赖边
- 继承体系中的方法派发

## MVP 规则

优先保证抽取结果稳定、可解释，而不是做激进推断。
如果分析器无法证明某条关系，应当降低置信度，而不是假装没有不确定性。

## 当前实现状态

当前分析器已经不再是纯占位实现。
它已经可以执行本地 AST 扫描，并产出：

- source file 记录
- 类型符号
- 方法符号
- `declares`、`extends`、`implements`、`uses_type` 关系
- 在同一个类内部、按方法名和参数个数可匹配的本地 `calls` 关系
- 当服务端传入显式重建列表时，只扫描指定的 Java 文件子集

当前限制：

- 还没有做 binding 解析
- 还没有做跨类型方法调用解析
- impact 传播仍然在服务端编排层完成，而不是在分析器模块内部完成
