# Review 模型

## 目的

这个产品不是一个泛化的代码图浏览器。
它是一个面向 review 的图谱工具，帮助开发者快速回答三个问题：

1. 改了什么
2. 哪些内容和这次修改直接相关
3. 接下来优先看哪里

## 默认审查单元

默认节点层级是 `type`。
只有当审查者选择某个具体类型时，才展开该类型内部的方法层级。

这样做是为了保证首屏可读，不把图谱做成只能“看热闹”的全量探索工具。

## 符号层级

- `project`
- `module`
- `package`
- `type`
- `method`

MVP 会保存项目、文件、类型和方法信息，但主界面重点是类型和方法。

## 关系类型

### 类型级

- `extends`
- `implements`
- `uses_type`

### 方法级

- `calls`
- `overrides`

### 包含关系

- `declares`

## 变更状态

- `unchanged`
- `added`
- `modified_api`
- `modified_impl`
- `deleted`
- `impacted`

## Review 优先级

### 最高优先级

- `modified_api`
- `deleted`

### 中等优先级

- `modified_impl`

### 上下文优先级

- `impacted`

## 界面规则

- 首次渲染只展示类型节点
- 默认过滤器优先展示变更类型及其一跳邻居
- 选中某个类型后，可以展开该类型内部的方法节点
- 方法展开范围应限制在当前选中类型内
- 右侧面板需要解释节点为什么被标记为 changed 或 impacted

## MVP 非目标

- 全项目范围的方法级全面展开
- 运行时执行链可视化
- 框架级隐藏依赖推断
