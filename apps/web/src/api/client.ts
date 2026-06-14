export type Project = {
  id: string;
  name: string;
  rootPath: string;
  buildTool: string;
  createdAt: string;
  updatedAt: string;
};

export type ProjectSnapshot = {
  id: string;
  projectId: string;
  baseSnapshotId: string | null;
  triggerType: string;
  gitCommit: string | null;
  gitCommitMessage: string | null;
  displayName: string;
  status: string;
  createdAt: string;
};

export type SnapshotDiagnostics = {
  id: string;
  projectId: string;
  baseSnapshotId: string | null;
  triggerType: string;
  gitCommit: string | null;
  gitCommitMessage: string | null;
  displayName: string;
  status: string;
  createdAt: string;
  requestedMode: string | null;
  effectiveMode: string | null;
  changeSource: string | null;
  includesWorkspaceChanges: boolean;
  note: string | null;
  fallbackReason: string | null;
  changedFiles: string[];
  renamedPaths: string[];
  rebuildPaths: string[];
  removedPaths: string[];
};

export type GraphNode = {
  id: string;
  name: string;
  qualifiedName: string;
  kind: string;
  status: string;
  layer?: number;
  order?: number;
  group?: string;
  groupOrder?: number;
  placement?: string;
};

export type GraphEdge = {
  source: string;
  target: string;
  type: string;
  confidence: string;
};

export type ClassGraph = {
  snapshotId: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
};

export type MethodGraph = {
  snapshotId: string;
  classId: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
};

export type SymbolChange = {
  symbolKey: string;
  changeType: string;
  reason: string;
};

export type ChangeSetReviewSymbol = {
  symbolKey: string;
  qualifiedName: string;
  displayName: string;
  kind: string;
  status: string;
  reviewRole: string;
};

export type ChangeSetReviewRisk = {
  level: string;
  score: number;
  reasons: string[];
};

export type ChangeSetPropagationPath = {
  fromSymbol: ChangeSetReviewSymbol;
  toSymbol: ChangeSetReviewSymbol;
  relationType: string;
  filePath: string | null;
  sourceLine: number | null;
};

export type ChangeSetTestFocusSuggestion = {
  symbol: ChangeSetReviewSymbol;
  priority: string;
  reason: string;
};

export type ChangeSetReviewResult = {
  projectId: string;
  snapshotId: string;
  snapshotDisplayName: string;
  note: string;
  changedFiles: string[];
  renamedPaths: string[];
  includesWorkspaceChanges: boolean;
  changedSymbols: ChangeSetReviewSymbol[];
  impactedSymbols: ChangeSetReviewSymbol[];
  reviewTargets: ChangeSetReviewSymbol[];
  propagationPaths: ChangeSetPropagationPath[];
  testFocusSuggestions: ChangeSetTestFocusSuggestion[];
  risk: ChangeSetReviewRisk;
  summary: string;
};

export type ChangeSetReviewMarkdownReport = {
  fileName: string;
  markdown: string;
};

export type ChangeSetReviewPayload = {
  snapshotId?: string | null;
  changeSource?: "git" | "manual";
  changedFiles?: string[];
  baseCommit?: string;
  targetCommit?: string;
};

export type SnapshotCompareRef = {
  id: string;
  displayName: string;
  gitCommit: string | null;
  gitCommitMessage: string | null;
};

export type SnapshotCompareSummary = {
  baseSymbolCount: number;
  targetSymbolCount: number;
  totalComparedSymbols: number;
  added: number;
  deleted: number;
  modifiedApi: number;
  modifiedImpl: number;
  unchanged: number;
  changed: number;
};

export type SnapshotCompareChange = {
  symbolKey: string;
  qualifiedName: string;
  displayName: string;
  kind: string;
  symbolType: string;
  filePath: string | null;
  changeType: string;
  reason: string;
};

export type SnapshotCompareRelationSummary = {
  baseRelationCount: number;
  targetRelationCount: number;
  totalComparedRelations: number;
  added: number;
  deleted: number;
  unchanged: number;
  changed: number;
};

export type SnapshotCompareRelationChange = {
  sourceSymbolKey: string;
  sourceDisplayName: string;
  sourceQualifiedName: string;
  targetSymbolKey: string;
  targetDisplayName: string;
  targetQualifiedName: string;
  relationType: string;
  filePath: string | null;
  sourceLine: number | null;
  changeType: string;
  reason: string;
};

export type SnapshotCompareResult = {
  projectId: string;
  baseSnapshot: SnapshotCompareRef;
  targetSnapshot: SnapshotCompareRef;
  summary: SnapshotCompareSummary;
  changes: SnapshotCompareChange[];
  relationSummary: SnapshotCompareRelationSummary;
  relationChanges: SnapshotCompareRelationChange[];
  note: string;
};

export type ProjectIndexResult = {
  project: Project;
  snapshot: ProjectSnapshot;
  typeCount: number;
  methodCount: number;
  relationCount: number;
  note: string;
};

type ApiError = Error & {
  code?: string;
  status?: number;
};

let apiBaseUrl = "";

export function setApiBaseUrl(nextApiBaseUrl: string) {
  apiBaseUrl = nextApiBaseUrl.trim().replace(/\/+$/, "");
}

function resolveRequestUrl(path: string) {
  if (!apiBaseUrl) {
    return path;
  }
  return `${apiBaseUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(resolveRequestUrl(path), {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    let code: string | undefined;
    try {
      const errorBody = (await response.json()) as { code?: string; message?: string };
      message = errorBody.message ?? message;
      code = errorBody.code;
    } catch {
      // Ignore JSON parsing errors and keep the generic message.
    }
    const error = new Error(message) as ApiError;
    error.code = code;
    error.status = response.status;
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export function listProjects() {
  return request<Project[]>("/api/projects");
}

export function createProject(payload: { name: string; rootPath: string }) {
  return request<Project>("/api/projects", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function deleteProject(projectId: string) {
  return request<void>(`/api/projects/${encodeURIComponent(projectId)}`, {
    method: "DELETE"
  });
}

export function listSnapshots(projectId: string) {
  return request<ProjectSnapshot[]>(`/api/projects/${encodeURIComponent(projectId)}/snapshots`);
}

export function getSnapshotDiagnostics(projectId: string, snapshotId: string) {
  return request<SnapshotDiagnostics>(
    `/api/projects/${encodeURIComponent(projectId)}/snapshots/${encodeURIComponent(snapshotId)}/diagnostics`
  );
}

export function compareSnapshots(projectId: string, baseSnapshotId: string, targetSnapshotId: string) {
  const params = new URLSearchParams({
    baseSnapshotId,
    targetSnapshotId
  });
  return request<SnapshotCompareResult>(
    `/api/projects/${encodeURIComponent(projectId)}/snapshots/compare?${params.toString()}`
  );
}

export function renameSnapshot(projectId: string, snapshotId: string, payload: { displayName: string }) {
  return request<ProjectSnapshot>(
    `/api/projects/${encodeURIComponent(projectId)}/snapshots/${encodeURIComponent(snapshotId)}`,
    {
      method: "PATCH",
      body: JSON.stringify(payload)
    }
  );
}

export function deleteSnapshot(projectId: string, snapshotId: string) {
  return request<void>(`/api/projects/${encodeURIComponent(projectId)}/snapshots/${encodeURIComponent(snapshotId)}`, {
    method: "DELETE"
  });
}

export function triggerIndex(
  projectId: string,
  payload: { mode: "full" | "incremental"; changeSource?: "git" | "manual"; changedFiles?: string[] }
) {
  return request<ProjectIndexResult>(`/api/projects/${encodeURIComponent(projectId)}/index`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getClassGraph(projectId: string, snapshotId?: string | null) {
  const params = new URLSearchParams();
  if (snapshotId) {
    params.set("snapshotId", snapshotId);
  }
  const query = params.toString();
  const suffix = query ? `?${query}` : "";
  return request<ClassGraph>(`/api/projects/${encodeURIComponent(projectId)}/graph/classes${suffix}`);
}

export function getChanges(projectId: string, snapshotId?: string | null) {
  const params = new URLSearchParams();
  if (snapshotId) {
    params.set("snapshotId", snapshotId);
  }
  const query = params.toString();
  const suffix = query ? `?${query}` : "";
  return request<SymbolChange[]>(`/api/projects/${encodeURIComponent(projectId)}/changes${suffix}`);
}

export function getMethodGraph(projectId: string, classId: string, snapshotId?: string | null) {
  const params = new URLSearchParams();
  params.set("classId", classId);
  if (snapshotId) {
    params.set("snapshotId", snapshotId);
  }
  const query = params.toString();
  const suffix = query ? `?${query}` : "";
  return request<MethodGraph>(`/api/projects/${encodeURIComponent(projectId)}/method-graph${suffix}`);
}

export function reviewChangeSet(
  projectId: string,
  payload: ChangeSetReviewPayload
) {
  return request<ChangeSetReviewResult>(`/api/projects/${encodeURIComponent(projectId)}/review/change-set`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function exportChangeSetReviewMarkdown(
  projectId: string,
  payload: ChangeSetReviewPayload
) {
  return request<ChangeSetReviewMarkdownReport>(`/api/projects/${encodeURIComponent(projectId)}/review/change-set/markdown`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
