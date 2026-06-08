# Indexing Flow

## Purpose

This document defines how the graph is built and refreshed after source changes.

## Full Index

```text
project import
  -> detect build tool and modules
  -> resolve source roots and classpath
  -> parse Java files with JDT bindings
  -> extract symbols
  -> extract relations
  -> compute hashes
  -> persist snapshot
```

## Incremental Index

```text
changed file list
  -> classify changed files
  -> fall back to full scan when no base snapshot or build metadata changed
  -> expand changed Java files with one-hop related files from the previous snapshot
  -> analyze only the rebuild set
  -> reuse unchanged files, symbols, and relations from the previous snapshot
  -> drop deleted files from the assembled snapshot
  -> diff old and new snapshot state
  -> derive one-hop impacted symbols, including deleted-neighbor cases
  -> publish review snapshot
```

## Change Sources

- manual file list input
- git diff from the latest snapshot commit to the current workspace state
- file watcher event

## Git-driven Incremental Mode

- if the latest snapshot stores a Git commit, use it as the diff base
- collect committed changes after that base commit
- add current uncommitted changes from `git diff HEAD`
- add current untracked files from `git ls-files --others --exclude-standard`
- if the latest snapshot has no committed Git base, fall back to the current working tree change set

## File Classification

### Java source change

Normalize the changed `.java` paths, rebuild those files, and include one-hop related files discovered from the previous snapshot relations.
If a changed Java file was deleted, remove its old records from the assembled snapshot and rebuild its surviving one-hop neighbors when possible.

### Build file change

If `pom.xml`, `build.gradle`, or module settings change, fall back to a full scan because the project model may have shifted.

### Resource change

Do not re-run analysis in the MVP. Reuse the previous snapshot data and persist a fresh snapshot boundary unless resource-to-code relation support is added later.

## Snapshot Diff Rules

Diff is based on stable `symbol_key`.

- new only: `added`
- old only: `deleted`
- `api_hash` changed: `modified_api`
- only `impl_hash` changed: `modified_impl`
- unchanged symbol touched by changed neighbor: `impacted`

## Impact Propagation Rules

### Method changes

- changed method -> `modified_api` or `modified_impl`
- direct callers -> `impacted`
- containing type -> changed
- caller types -> `impacted`

### Type changes

- changed type -> `modified_api` or `modified_impl`
- direct users -> `impacted`
- direct neighbors of a deleted type -> `impacted`
- subclasses and overriding methods may also become `impacted`

## Current Simplification

The MVP impact model is intentionally shallow:

- one-hop caller propagation first
- one-hop type user propagation first
- deeper propagation can be added later behind an explicit depth setting
