# Roadmap

## Milestone 0

- initialize repo structure
- add module boundaries
- add schema and API drafts
- add minimal bootable server
- align local storage on SQLite

## Milestone 1

- import a Maven Java project
- discover source roots and modules
- resolve JDT classpath
- parse types and methods

## Milestone 2

- extract `extends`, `implements`, `uses_type`, `calls`, and `overrides`
- persist snapshots
- expose class graph query API

## Milestone 3

- support incremental re-index from changed files
- compute snapshot diff
- mark changed and impacted symbols

## Milestone 4

- render class graph in the web UI
- expand selected class into method nodes
- highlight changed and impacted nodes

## Milestone 5

- support commit-range or change-set based review analysis
- map Git changes to changed and impacted symbols
- expose review summary APIs for one change set

## Milestone 6

- add risk scoring and explainable review rules
- support symbol path queries and propagation tracing
- present review focus and impact reasoning in the UI

## Milestone 7

- export review reports in Markdown-first form
- compare historical snapshots at symbol level
- compare relation evolution across snapshots
- add AI-assisted review explanations on top of graph evidence
