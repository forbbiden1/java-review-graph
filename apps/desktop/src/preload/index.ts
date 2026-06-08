import { contextBridge, ipcRenderer } from "electron";

type LanguageMode = "en" | "zh";

type UiSettings = {
  language: LanguageMode;
  apiBaseUrl: string;
};

type RuntimeInfo = {
  defaultApiBaseUrl: string;
  mode: "desktop";
  platform: string;
};

const OPEN_SETTINGS_EVENT = "java-review-graph:open-settings";
const RELOAD_WORKSPACE_EVENT = "java-review-graph:reload-workspace";

const desktopApi = {
  getRuntimeInfo: (): RuntimeInfo => ({
    defaultApiBaseUrl: "http://127.0.0.1:8080",
    mode: "desktop",
    platform: process.platform
  }),
  getSettings: (): Promise<UiSettings> => ipcRenderer.invoke("desktop:get-settings"),
  setSettings: (update: Partial<UiSettings>): Promise<UiSettings> => ipcRenderer.invoke("desktop:set-settings", update)
};

ipcRenderer.on("app:open-settings", () => {
  window.dispatchEvent(new Event(OPEN_SETTINGS_EVENT));
});

ipcRenderer.on("app:reload-workspace", () => {
  window.dispatchEvent(new Event(RELOAD_WORKSPACE_EVENT));
});

contextBridge.exposeInMainWorld("javaReviewGraphDesktop", desktopApi);
