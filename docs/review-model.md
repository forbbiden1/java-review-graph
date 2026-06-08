# Review Model

## Purpose

The product is not a generic code graph browser.
It is a review-oriented graph that helps a developer answer three questions:

1. What changed
2. What is directly related to the change
3. What should be inspected next

## Default Review Unit

The default node level is `type`.
The method level is expanded only when a reviewer selects a specific type.

This keeps the first screen readable and makes the graph useful for review instead of exploration-only browsing.

## Symbol Levels

- `project`
- `module`
- `package`
- `type`
- `method`

The MVP stores project, file, type, and method information, but the main graph view focuses on type and method.

## Relation Types

### Type-Level

- `extends`
- `implements`
- `uses_type`

### Method-Level

- `calls`
- `overrides`

### Containment

- `declares`

## Change States

- `unchanged`
- `added`
- `modified_api`
- `modified_impl`
- `deleted`
- `impacted`

## Review Priorities

### Highest priority

- `modified_api`
- `deleted`

### Medium priority

- `modified_impl`

### Context priority

- `impacted`

## UI Rules

- first render only type nodes
- default filter should prefer changed types and one-hop neighbors
- a selected type may expand into method nodes
- method expansion should stay local to the selected type
- the right panel should explain why a node is marked as changed or impacted

## Non-Goals for MVP

- full project-wide method graph expansion
- runtime execution trace visualization
- framework-level hidden dependency inference
