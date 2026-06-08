# React Flow + ELK Migration

## Goal

Replace the custom graph canvas with `React Flow + ELKJS` while preserving the current review workflow.

## Checklist

1. Add `@xyflow/react` and `elkjs` to the web app and Electron app.
2. Replace the old custom graph renderer with a shared `ReactFlowProvider` based renderer.
3. Switch automatic layout to ELK layered layout.
4. Preserve current interaction semantics:
   - single click centers and selects a node
   - double click scopes to related nodes
   - direct and connected scope toggles
   - wheel zoom and slider zoom
   - drag node and persist position overrides
   - reset view clears persisted viewport and overrides
5. Preserve class graph and method graph with the same shared renderer.
6. Keep existing change-status color semantics and edge labels.
7. Remove dead styles and old custom layout code.
8. Rebuild browser and desktop targets.

## Executed

- Added dependencies in:
  - `apps/web/package.json`
  - `apps/desktop/package.json`
- Replaced the graph implementation in:
  - `apps/web/src/graph/GraphCanvas.tsx`
- Removed the old layout engine:
  - `apps/web/src/graph/layout.ts`
- Added React Flow specific styles and node/edge styling in:
  - `apps/web/src/styles.css`
- Removed legacy custom-canvas style leftovers from:
  - `apps/web/src/styles.css`

## Current Result

- Graph layout is now driven by ELK layered layout with rightward flow.
- Graph rendering, drag, pan, zoom, minimap, and viewport control are handled by React Flow.
- Node drag positions and viewport state still use the existing scene persistence storage.
- Class graph and method graph both use the same renderer.
- Scope mode still supports direct neighbors and connected subgraphs.

## Verification

- `npm run build` in `apps/web`
- `npm run build` in `apps/desktop`

## Notes

- The new bundle is larger because React Flow and ELK are now included.
- If needed later, the graph renderer can be split into a dedicated chunk without changing the architecture.
