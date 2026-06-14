# Campus Project Plan

## Positioning

Java Review Graph can be presented as:

`a local-first Java code review and change impact analysis tool`

This positioning is stronger than a generic code browser because it combines:

- static analysis
- snapshot-based change tracking
- graph query and visualization
- review-oriented risk and impact explanation

For campus recruiting, the project should emphasize:

- real engineering scenario instead of demo-only CRUD
- clear analyzer, backend, storage, and frontend layering
- explainable review output instead of raw graph dumps
- visible end-to-end workflow from Git change to review result

## High-Value Showcase Features

### 1. Git commit or PR impact analysis

Goal:

- accept a commit range, branch diff, or patch-like change set
- map changed files to changed symbols
- derive impacted classes and methods
- generate a review summary for the selected change set

Expected output:

- changed files
- changed symbols
- impacted symbols
- affected call chains
- recommended review focus

Why it matters:

- closest to a real code review workflow
- easy to demo in a short interview session
- connects analyzer, snapshot diff, graph query, and UI

### 2. Change risk scoring

Goal:

- assign low, medium, or high risk to one change set
- explain the score with concrete rules

Candidate signals:

- public API changed
- high fan-out symbol changed
- cross-module propagation
- core entry or shared utility touched
- affected call-chain depth

Why it matters:

- shows graph-derived reasoning rather than visualization only
- creates a strong story around engineering value and explainability

### 3. Call path and dependency path analysis

Goal:

- trace shortest path or bounded-depth paths between two symbols
- answer “who depends on this class” and “how can this change spread”

Expected queries:

- source symbol to target symbol path
- upstream dependents of one symbol
- downstream impact path from one changed symbol

Why it matters:

- demonstrates graph algorithm value directly
- produces strong visual demos for interviews

### 4. Historical snapshot comparison

Goal:

- compare any two snapshots
- explain added, removed, and updated symbols or relations
- visualize structural evolution over time

Expected output:

- symbol diff summary
- relation diff summary
- evolution timeline for one class or package

Why it matters:

- upgrades the project from “current graph viewer” to “code evolution review tool”

### 5. Review report export

Goal:

- export a review packet as Markdown first, then optionally HTML or PDF

Suggested content:

- change summary
- impacted graph excerpt
- risk level and explanation
- suggested regression scope

Why it matters:

- turns the project into a usable tool instead of only an interactive demo

### 6. AI-assisted explanation

Goal:

- use already-computed graph and diff results as structured input
- generate concise review explanations and test suggestions

Boundaries:

- AI should summarize analyzer output
- AI should not replace core graph derivation

Why it matters:

- adds a modern layer without weakening technical depth
- works well as a “static analysis plus graph plus AI” narrative

## Recommended Development Sequence

If the project is optimized for campus recruiting, the best feature sequence is:

1. Git commit or PR impact analysis
2. Change risk scoring
3. Review report export
4. Call path analysis
5. Historical snapshot comparison
6. AI-assisted explanation

The first three items already form a strong interview-ready version.

## Rough Delivery Plan

## Phase 1: Review change set workflow

Scope:

- select commit range or Git diff base
- build changed-symbol and impacted-symbol summary
- show review-focused change view in the UI
- expose backend APIs for change-set analysis

Primary deliverables:

- `POST /api/projects/{projectId}/review/change-set`
- change summary panel in desktop and web UI
- persisted change-set analysis result or derived snapshot view

Demo value:

- import project
- choose a commit range
- immediately show impacted graph and review targets

## Phase 2: Risk and path reasoning

Scope:

- add risk scoring rules
- add path queries between symbols
- explain why a change is risky

Primary deliverables:

- risk score API and rule model
- path query API
- UI badges and explanation panel

Demo value:

- click one changed class
- show propagation path and risk explanation

## Phase 3: Report output and portfolio polish

Scope:

- export Markdown report
- add snapshot-to-snapshot comparison summary
- improve presentation polish for demos

Primary deliverables:

- report export action
- snapshot comparison view
- stronger empty states, loading states, and demo fixtures

Demo value:

- produce a shareable report after one analysis run

## Phase 4: AI explanation layer

Scope:

- turn structured graph findings into readable review advice
- generate test-focus suggestions from risk and path results

Primary deliverables:

- prompt input model based on change summary and graph evidence
- AI explanation panel with explicit evidence references

Demo value:

- combines traditional static analysis with an AI explanation layer

## Implementation Notes

- Prefer keeping the core reasoning deterministic.
- Treat AI as a presentation and explanation layer on top of graph evidence.
- Keep every risk result explainable with stored signals or graph paths.
- Preserve local-first behavior as the default experience.

## Interview Presentation Tips

- Demo one realistic Java repository, not an artificial toy example.
- Show one complete flow: import project -> index snapshot -> select change set -> inspect impact -> export report.
- Prepare 2 or 3 concrete risk rules and explain why they are useful.
- Be ready to explain the split between analyzer, server, SQLite, and frontend responsibilities.
