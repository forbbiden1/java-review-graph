import type { Project, ProjectSnapshot } from "../api/client";

export type ClassGraphDisplayMode = "full" | "incremental";

export type IndexChangeSource = "git" | "manual";

export type ExpandedGraphView = "class" | "method" | null;

export type ContextMenuState =
  | {
      kind: "project";
      project: Project;
      x: number;
      y: number;
    }
  | {
      kind: "snapshot";
      snapshot: ProjectSnapshot;
      x: number;
      y: number;
    };

export type SnapshotGroup = {
  key: string;
  title: string;
  shortCommit: string | null;
  snapshots: ProjectSnapshot[];
};

export type SnapshotDiagnosticsCopy = {
  baseSnapshot: string;
  changeSource: string;
  changedFiles: string;
  effectiveMode: string;
  emptyBody: string;
  emptyTitle: string;
  fallbackReason: string;
  gitCommit: string;
  loadingBody: string;
  loadingTitle: string;
  none: string;
  renamedPaths: string;
  rebuildPaths: string;
  removedPaths: string;
  requestedMode: string;
  subtitle: string;
  summary: string;
  title: string;
  unavailableBody: string;
  unavailableTitle: string;
  workspaceChanges: string;
  yes: string;
  no: string;
};
