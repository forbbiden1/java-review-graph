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
