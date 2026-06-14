# Web Design

## Purpose

`apps/web` renders the review experience for the code graph.

## Main Screens

### Project bootstrap

- choose or inspect imported project
- see snapshot status

### Review screen

- left: changed symbol list and filters
- center: graph canvas
- right: detail panel

### Snapshot history

- inspect previous indexing results
- re-open old review context

## Graph Interaction Rules

- default view is type-level only
- click a type to expand methods inside that type
- do not expand methods for unrelated types by default
- selecting a changed symbol should center and highlight it
- edge and node styling should explain change status clearly

## Visual Status Model

- `added`
  green emphasis
- `modified_api`
  red emphasis
- `modified_impl`
  amber or orange emphasis
- `deleted`
  muted or dashed treatment
- `impacted`
  secondary warning emphasis

## Data Needs

The frontend expects API payloads that already separate:

- class graph nodes and edges
- method graph for one selected class
- symbol detail data
- change reason and impact reason

## MVP Rule

The first UI should optimize for readability over density.
If a graph becomes too large, the frontend should narrow scope rather than display everything.

## Current Implementation State

The current frontend already provides:

- project import form
- imported project list
- full and incremental index controls
- snapshot list
- class graph view
- method graph expansion for the selected class
- change list and selection inspector

Current implementation notes:

- the graph renderer uses React Flow for interaction and ELK layered layout for node placement
- class graph and method graph share the same renderer and scene persistence logic
- the graph renderer is now lazy-loaded so the main application chunk stays small while the graph chunk loads on demand
- ELK layout code now ships as a separate worker asset, which keeps the interactive graph chunk much smaller
- React Flow and related graph interaction dependencies are split into a dedicated `graph-flow` vendor chunk, so startup code stays separate from canvas interaction code
- `GraphCanvas.tsx` now focuses on canvas orchestration; graph model types, ELK layout, viewport math, scene helpers, and node or edge renderers live in separate `src/graph/*` modules
- persistent graph view transitions now go through a reducer, so scope changes, node position overrides, scene restore, reset, and viewport updates share one predictable state path
- snapshot history now renders commit-grouped sections through a dedicated component and reveals long groups incrementally, which keeps the sidebar responsive when one project accumulates many snapshots
- large graphs now start in a progressive preview mode that keeps high-signal nodes first and lets the user switch to the full graph on demand, reducing initial layout pressure for very large snapshots
- Vite proxies `/api` to `http://localhost:8080` for local development
- the UI is wired to the current backend endpoints and SQLite-backed snapshots
- the right-side inspector now also runs change-set review, shows deterministic risk and review targets, and exports a Markdown report for sharing
- the change-set review controls are independent from indexing controls and support Git auto, manual file lists, and explicit commit-range inputs
- incremental indexing now also exposes a small impact-depth control so demos can compare one-hop and multi-hop impacted-symbol expansion
- the same review panel now surfaces structured risk factors with score contribution and evidence, so interview demos can explain exactly why one change set is low, medium, or high risk
- the change-set review panel also shows direct propagation paths so users can explain why one changed symbol affects a downstream review target
- the review panel now also requests one bounded symbol-path trace between a changed symbol and an impacted symbol, so the demo can show a multi-hop impact explanation beyond one-hop propagation
- the same panel now highlights deterministic test-focus suggestions so the demo can end with concrete regression or integration targets
- the same inspector now also supports snapshot-to-snapshot compare, so users can choose a baseline snapshot and inspect deterministic symbol diffs plus structural relation evolution before running review
- `App.tsx` is now limited to workspace state and orchestration; shared panels live in `src/app/components.tsx`, while graph, snapshot, and review display helpers live in `src/app/utils.ts`
