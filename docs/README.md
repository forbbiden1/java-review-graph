# Docs Index

This directory is the development documentation entry point for Java Review Graph.
Read the documents in the order below when starting work on a new module.

Chinese documentation is available at [zh-CN/README.md](./zh-CN/README.md).

## Reading Order

1. [architecture.md](./architecture.md)
   Overall system goal, module boundaries, and MVP scope.
2. [review-model.md](./review-model.md)
   Product-facing review concepts: symbol levels, relation types, change states, and UI focus.
3. [indexing-flow.md](./indexing-flow.md)
   Full and incremental indexing flow, snapshot diff, and impact propagation.
4. [analyzer-design.md](./analyzer-design.md)
   Java analyzer responsibilities, JDT pipeline, and extraction stages.
5. [server-design.md](./server-design.md)
   Backend responsibilities, persistence shape, and API layering.
6. [web-design.md](./web-design.md)
   Frontend screen structure and graph interaction rules.
7. [api.md](./api.md)
   External API draft.
8. [schema.md](./schema.md)
   Storage model and symbol identity rules.
9. [schema.sql](./schema.sql)
   Initial SQL draft.
10. [dev-setup.md](./dev-setup.md)
    Local development setup and current build notes.
11. [roadmap.md](./roadmap.md)
    Milestone order.
12. [todo.md](./todo.md)
    Prioritized post-MVP follow-up work.

## Document Roles

- Product and architecture:
  `architecture.md`, `review-model.md`
- Analyzer and indexing:
  `analyzer-design.md`, `indexing-flow.md`
- Backend and persistence:
  `server-design.md`, `api.md`, `schema.md`, `schema.sql`
- Frontend:
  `web-design.md`
- Execution support:
  `dev-setup.md`, `roadmap.md`, `todo.md`

## Current Rule

When a new implementation task starts, update the corresponding document first if:

- the module boundary changes
- the symbol model changes
- the indexing flow changes
- the API contract changes
- the review interaction changes

## Language Entry

- English: [README.md](./README.md)
- Chinese: [zh-CN/README.md](./zh-CN/README.md)
