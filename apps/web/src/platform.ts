export type LanguageMode = "en" | "zh";

export type UiSettings = {
  language: LanguageMode;
  apiBaseUrl: string;
};

export type RuntimeInfo = {
  mode: "web" | "desktop";
  platform: string;
  defaultApiBaseUrl: string;
};

export type GraphSceneView = {
  offsetX: number;
  offsetY: number;
  zoom: number;
};

export type StoredGraphScene = {
  version: 1;
  nodeOverrides: Record<string, { x: number; y: number }>;
  scopedNodeId: string | null;
  scopeMode: "connected" | "direct";
  view: GraphSceneView;
};

export type DesktopBridge = {
  getRuntimeInfo: () => RuntimeInfo;
  getSettings: () => Promise<UiSettings>;
  setSettings: (update: Partial<UiSettings>) => Promise<UiSettings>;
};

const SETTINGS_STORAGE_KEY = "java-review-graph.ui-settings";
const GRAPH_SCENE_STORAGE_KEY_PREFIX = "java-review-graph.graph-scene:";
const DEFAULT_DESKTOP_API_BASE_URL = "http://127.0.0.1:8080";
const OPEN_SETTINGS_EVENT = "java-review-graph:open-settings";
const RELOAD_WORKSPACE_EVENT = "java-review-graph:reload-workspace";

function getDesktopBridge(): DesktopBridge | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  return window.javaReviewGraphDesktop;
}

function detectLanguage(): LanguageMode {
  if (typeof navigator !== "undefined" && navigator.language.toLowerCase().startsWith("zh")) {
    return "zh";
  }
  return "en";
}

export function sanitizeApiBaseUrl(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }
  return trimmed.replace(/\/+$/, "");
}

export function resolveRuntimeInfo(): RuntimeInfo {
  const desktopBridge = getDesktopBridge();
  if (desktopBridge) {
    return desktopBridge.getRuntimeInfo();
  }

  return {
    mode: "web",
    platform: "web",
    defaultApiBaseUrl: ""
  };
}

export function normalizeSettings(value: Partial<UiSettings> | null | undefined, runtime = resolveRuntimeInfo()): UiSettings {
  const fallbackApiBaseUrl = runtime.mode === "desktop" ? runtime.defaultApiBaseUrl : "";
  const normalizedApiBaseUrl = sanitizeApiBaseUrl(value?.apiBaseUrl ?? fallbackApiBaseUrl);

  return {
    language: value?.language === "zh" ? "zh" : value?.language === "en" ? "en" : detectLanguage(),
    apiBaseUrl: normalizedApiBaseUrl || fallbackApiBaseUrl
  };
}

export function defaultSettings(runtime = resolveRuntimeInfo()): UiSettings {
  return normalizeSettings(
    {
      language: detectLanguage(),
      apiBaseUrl: runtime.mode === "desktop" ? runtime.defaultApiBaseUrl || DEFAULT_DESKTOP_API_BASE_URL : ""
    },
    runtime
  );
}

export async function loadUiSettings(): Promise<UiSettings> {
  const runtime = resolveRuntimeInfo();
  const desktopBridge = getDesktopBridge();

  if (desktopBridge) {
    const storedSettings = await desktopBridge.getSettings();
    return normalizeSettings(storedSettings, runtime);
  }

  if (typeof window === "undefined") {
    return defaultSettings(runtime);
  }

  try {
    const rawValue = window.localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (!rawValue) {
      return defaultSettings(runtime);
    }

    return normalizeSettings(JSON.parse(rawValue) as Partial<UiSettings>, runtime);
  } catch {
    return defaultSettings(runtime);
  }
}

export async function saveUiSettings(update: Partial<UiSettings>, currentSettings: UiSettings): Promise<UiSettings> {
  const runtime = resolveRuntimeInfo();
  const desktopBridge = getDesktopBridge();

  if (desktopBridge) {
    const storedSettings = await desktopBridge.setSettings(update);
    return normalizeSettings(storedSettings, runtime);
  }

  const nextSettings = normalizeSettings(
    {
      ...currentSettings,
      ...update
    },
    runtime
  );

  if (typeof window !== "undefined") {
    window.localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(nextSettings));
  }

  return nextSettings;
}

export function loadGraphScene(storageKey: string | null | undefined): StoredGraphScene | null {
  if (!storageKey || typeof window === "undefined") {
    return null;
  }

  try {
    const rawValue = window.localStorage.getItem(resolveGraphSceneStorageKey(storageKey));
    if (!rawValue) {
      return null;
    }

    return normalizeGraphScene(JSON.parse(rawValue) as Partial<StoredGraphScene>);
  } catch {
    return null;
  }
}

export function saveGraphScene(storageKey: string | null | undefined, scene: StoredGraphScene) {
  if (!storageKey || typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(resolveGraphSceneStorageKey(storageKey), JSON.stringify(normalizeGraphScene(scene)));
}

export function clearGraphScene(storageKey: string | null | undefined) {
  if (!storageKey || typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(resolveGraphSceneStorageKey(storageKey));
}

export function subscribeToOpenSettings(callback: () => void) {
  const desktopBridge = getDesktopBridge();
  if (!desktopBridge || typeof window === "undefined") {
    return () => undefined;
  }
  const listener = () => callback();
  window.addEventListener(OPEN_SETTINGS_EVENT, listener);
  return () => window.removeEventListener(OPEN_SETTINGS_EVENT, listener);
}

export function subscribeToReloadWorkspace(callback: () => void) {
  const desktopBridge = getDesktopBridge();
  if (!desktopBridge || typeof window === "undefined") {
    return () => undefined;
  }
  const listener = () => callback();
  window.addEventListener(RELOAD_WORKSPACE_EVENT, listener);
  return () => window.removeEventListener(RELOAD_WORKSPACE_EVENT, listener);
}

function resolveGraphSceneStorageKey(storageKey: string) {
  return `${GRAPH_SCENE_STORAGE_KEY_PREFIX}${storageKey}`;
}

function normalizeGraphScene(value: Partial<StoredGraphScene> | null | undefined): StoredGraphScene {
  return {
    version: 1,
    nodeOverrides: normalizeNodeOverrides(value?.nodeOverrides),
    scopedNodeId: typeof value?.scopedNodeId === "string" && value.scopedNodeId.trim() ? value.scopedNodeId : null,
    scopeMode: value?.scopeMode === "connected" ? "connected" : "direct",
    view: normalizeGraphSceneView(value?.view)
  };
}

function normalizeGraphSceneView(value: Partial<GraphSceneView> | null | undefined): GraphSceneView {
  return {
    offsetX: Number.isFinite(value?.offsetX) ? Number(value?.offsetX) : 0,
    offsetY: Number.isFinite(value?.offsetY) ? Number(value?.offsetY) : 0,
    zoom: Number.isFinite(value?.zoom) ? Number(value?.zoom) : 1
  };
}

function normalizeNodeOverrides(
  value: Record<string, { x: number; y: number }> | null | undefined
): Record<string, { x: number; y: number }> {
  if (!value) {
    return {};
  }

  return Object.fromEntries(
    Object.entries(value).filter(
      ([nodeId, point]) =>
        Boolean(nodeId) &&
        point !== null &&
        typeof point === "object" &&
        Number.isFinite(point.x) &&
        Number.isFinite(point.y)
    )
  );
}
