# TODO List

This document tracks the highest-value follow-up work for Java Review Graph after the current MVP.
The focus is accuracy first, then maintainability, then performance and polish.

## P0: Analyzer Accuracy

- [ ] Add real JDT binding resolution for types and methods.
  Done when cross-file type references are resolved through bindings instead of name-only fallback in the common case.
- [ ] Resolve cross-type method calls.
  Done when method-call edges can point to methods declared in other types, not only same-class matches.
- [ ] Improve import, package, and fully-qualified name handling.
  Done when relation extraction produces fewer unresolved `uses_type` edges for normal Maven projects.
- [ ] Add relation confidence and fallback reason metadata.
  Done when each non-trivial relation can explain whether it came from exact binding or fallback inference.

## P0: Incremental Index Reliability

- [x] Persist the actual changed file set for each snapshot.
  Done when the UI and API can show which files were used to build an incremental snapshot.
- [x] Record why indexing ran in `full` or `incremental` mode.
  Done when fallback reasons such as build-file changes or missing base snapshot are visible.
- [x] Handle rename and move scenarios explicitly.
  Done when file renames do not silently appear as unrelated delete-plus-add without explanation.
- [ ] Make impact propagation depth configurable.
  Done when one-hop stays the default but deeper propagation can be enabled intentionally.

## P1: Backend Refactor

- [ ] Split `ProjectIndexService` into smaller services.
  Suggested split: Git change collection, incremental planning, snapshot assembly, diff calculation, and snapshot metadata.
- [ ] Isolate graph assembly from transport and persistence concerns.
  Done when indexing logic can be tested without going through controller-level flows.
- [x] Add snapshot diagnostics APIs.
  Done when the frontend can query diff base, collected files, fallback reason, and rebuild summary directly.

## P1: Frontend Refactor

- [x] Split `App.tsx` by feature areas.
  Suggested split: project workspace, snapshot panel, change panel, graph workspace, and settings.
- [ ] Split `GraphCanvas.tsx` by responsibility.
  Suggested split: layout, viewport state, node interaction, edge rendering, and persisted scene state.
- [ ] Centralize graph state transitions.
  Done when fullscreen, filtered graph views, focus changes, and reset behavior are driven by predictable state instead of scattered handlers.

## P1: Graph Performance and Usability

- [ ] Reduce the main web bundle size.
  Done when graph-heavy screens and settings are lazy-loaded and the main chunk warning is reduced or eliminated.
- [ ] Virtualize long snapshot and change lists.
  Done when very large histories remain responsive.
- [ ] Improve large-graph rendering strategy.
  Candidates: progressive node reveal, edge simplification, staged layout, and viewport-based rendering.
- [x] Add an indexing diagnostics panel in the UI.
  Done when users can inspect snapshot source, changed files, fallback reasons, and rebuild scope without reading logs.

## P1: Showcase Features

- [ ] Add Git change-set review analysis.
  Done when one commit range or diff base can produce changed-symbol, impacted-symbol, and review-summary output.
- [ ] Add explainable change risk scoring.
  Done when low, medium, and high risk levels are derived from stored signals and graph evidence.
- [ ] Add symbol path and impact trace queries.
  Done when the UI can show dependency or call paths between review-relevant symbols.
- [ ] Add Markdown review report export.
  Done when one analysis run can be exported as a shareable review summary.
- [x] Add historical snapshot comparison.
  Done for symbol-level evolution when two snapshots can show added, deleted, API-modified, and implementation-modified symbols side by side.
- [x] Add relation evolution to historical snapshot comparison.
  Done when dependency and call relation additions and deletions can be inspected alongside symbol changes.
- [ ] Add AI-assisted review explanation.
  Done when graph-derived evidence can be summarized into readable review notes and test suggestions.

## P2: SQLite and Persistence

- [x] Review and add database indexes for high-frequency queries.
  Done for current snapshot, source-file, symbol, and relation read paths used by graph queries and incremental snapshot maintenance.
- [ ] Enable or validate WAL mode and batched writes.
  Done when indexing throughput and UI read concurrency are stable on larger projects.
- [ ] Add snapshot retention and cleanup tools.
  Done when old snapshots, orphan rows, and stale layout state can be pruned safely.

## P2: Test Coverage

- [ ] Add integration tests for Git-driven incremental indexing.
  Cover committed-only, uncommitted-only, mixed changes, untracked files, and no-base-snapshot flows.
- [x] Add rename, delete, and build-file fallback tests.
- [ ] Add UI smoke tests for key review flows.
  Focus on project import, unsupported-language rejection, graph filtering, fullscreen mode, snapshot rename, and snapshot delete.

## P2: Documentation Sync

- [x] Update `roadmap.md` to reflect the current Electron desktop direction and post-MVP showcase milestones.
- [ ] Add a troubleshooting guide.
  Focus on unsupported project import, backend connection failure, incremental fallback reasons, and graph readability issues.
- [ ] Keep API and indexing docs aligned with implementation changes.

## Suggested Implementation Order

1. Analyzer accuracy
2. Incremental diagnostics and rename handling
3. Backend refactor
4. Frontend refactor
5. Performance and persistence
6. Broader test coverage
7. Documentation cleanup
