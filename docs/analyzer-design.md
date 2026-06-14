# Analyzer Design

## Purpose

`apps/analyzer-jdt` is the Java analysis engine.
Its job is to turn a Java project into symbols, relations, hashes, and diff-ready data.

## Core Responsibilities

- detect project modules and source roots
- resolve classpath from Maven or Gradle layout
- parse Java source with Eclipse JDT
- resolve bindings for types and methods
- extract type and method symbols
- extract relation edges
- compute stable symbol keys and hashes

## Package Roles

- `project`
  project and module discovery
- `parser`
  JDT parser configuration and AST batch execution
- `extractor`
  symbol and relation extraction
- `diff`
  snapshot comparison
- `impact`
  changed-neighbor propagation

## Planned Pipeline

1. `ProjectModelBuilder`
   discover modules, source roots, and build tool hints
2. `ClasspathResolver`
   prepare binary and source dependencies for binding resolution
3. `AstBatchParser`
   run `ASTParser.createASTs(...)`
4. `TypeExtractor`
   collect class, interface, enum, record, and annotation symbols
5. `MethodExtractor`
   collect methods and constructors
6. `RelationExtractor`
   collect `extends`, `implements`, `uses_type`, `calls`, and `overrides`
7. `HashBuilder`
   derive API and implementation hashes
8. `SnapshotDiffer`
   compare previous and current graph state
9. `ImpactAnalyzer`
   mark impacted symbols

## Binding Expectations

The analyzer should prefer exact semantic binding when available.
If binding fails, the relation may still be emitted with lower confidence and explicit fallback metadata.

## Confidence Levels

- `exact`
- `possible`
- `unresolved`

## Known Risk Areas

- Lombok-generated members
- reflection-driven type loading
- incomplete classpath
- multi-module dependency edges
- method dispatch across inheritance hierarchies

## MVP Rule

Prefer stable and explainable extraction over aggressive inference.
If the analyzer cannot prove a relation, it should degrade confidence rather than hide uncertainty.

## Current Implementation State

The current analyzer is no longer a pure placeholder.
It already performs a local AST scan and produces:

- source file records
- type symbols
- method symbols
- `declares`, `extends`, `implements`, and `uses_type` relations
- binding-backed `extends`, `implements`, and `uses_type` relations when JDT can resolve the target
- cross-type `calls` relations when method bindings or stable owner-plus-signature matching can resolve the target
- incremental file-subset scans when the server passes an explicit rebuild list

Current limitations:

- binding quality still depends on the local source/classpath completeness
- unresolved third-party or incomplete-classpath targets still fall back to lower-confidence relation output
- overload and dispatch resolution still prefers declared target signatures, not runtime polymorphism
- impact propagation is still orchestrated in the server layer, not inside the analyzer module
