# React Flow + ELK 迁移

## 目标

用 `React Flow + ELKJS` 替换当前自定义图画布，同时保留现有 review 流程。

## 清单

1. 在 web app 和 Electron app 中加入 `@xyflow/react` 与 `elkjs`。
2. 用基于 `ReactFlowProvider` 的共享渲染器替换旧的自定义图渲染。
3. 自动布局切换为 ELK 的 layered layout。
4. 保留现有交互语义：
   - 单击居中并选中节点
   - 双击聚焦到关联节点
   - 直接范围和连通范围切换
   - 滚轮缩放和滑块缩放
   - 拖拽节点并持久化位置覆盖
   - 重置视图时清空持久化 viewport 和覆盖位置
5. 类图和方法图继续使用同一个共享渲染器。
6. 保持现有变更状态颜色语义和边标签。
7. 移除无用样式和旧的自定义布局代码。
8. 重新构建浏览器端和桌面端目标。

## 已完成

- 在以下位置加入依赖：
  - `apps/web/package.json`
  - `apps/desktop/package.json`
- 将图实现替换为：
  - `apps/web/src/graph/GraphCanvas.tsx`
- 移除了旧布局引擎：
  - `apps/web/src/graph/layout.ts`
- 在以下位置加入 React Flow 专用样式和节点/边样式：
  - `apps/web/src/styles.css`
- 从以下位置移除了旧自定义画布的残留样式：
  - `apps/web/src/styles.css`

## 当前结果

- 图布局现在由 ELK 的 layered layout 驱动，整体向右展开。
- 图渲染、拖拽、平移、缩放、小地图和视口控制都由 React Flow 处理。
- 节点拖拽位置和视口状态仍然使用现有的 scene 持久化存储。
- 类图和方法图继续共用同一个渲染器。
- 范围模式仍然支持直接邻居和连通子图。

## 验证

- `npm run build` in `apps/web`
- `npm run build` in `apps/desktop`

## 备注

- 由于引入了 React Flow 和 ELK，新 bundle 会更大。
- 如果后续需要，图渲染器可以拆成独立 chunk，而不影响整体架构。
