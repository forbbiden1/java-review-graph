import { app, BrowserWindow, ipcMain, Menu, shell } from "electron";
import Store from "electron-store";
import path from "node:path";
import { fileURLToPath } from "node:url";

type LanguageMode = "en" | "zh";

type DesktopSettings = {
  apiBaseUrl: string;
  language: LanguageMode;
};

type RuntimeInfo = {
  defaultApiBaseUrl: string;
  mode: "desktop";
  platform: string;
};

const DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PRODUCT_NAME = "Java Review Graph";

let mainWindow: BrowserWindow | null = null;
let settingsStore: Store<DesktopSettings>;

function detectLanguage(): LanguageMode {
  return app.getLocale().toLowerCase().startsWith("zh") ? "zh" : "en";
}

function sanitizeApiBaseUrl(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }
  return trimmed.replace(/\/+$/, "");
}

function getDefaultSettings(): DesktopSettings {
  return {
    apiBaseUrl: DEFAULT_API_BASE_URL,
    language: detectLanguage()
  };
}

function normalizeSettings(value: Partial<DesktopSettings> | undefined): DesktopSettings {
  const defaults = getDefaultSettings();
  return {
    language: value?.language === "zh" ? "zh" : value?.language === "en" ? "en" : defaults.language,
    apiBaseUrl: sanitizeApiBaseUrl(value?.apiBaseUrl ?? defaults.apiBaseUrl) || defaults.apiBaseUrl
  };
}

function getRuntimeInfo(): RuntimeInfo {
  return {
    defaultApiBaseUrl: DEFAULT_API_BASE_URL,
    mode: "desktop",
    platform: process.platform
  };
}

function getSettings() {
  return normalizeSettings({
    apiBaseUrl: settingsStore.get("apiBaseUrl"),
    language: settingsStore.get("language")
  });
}

function createWindow() {
  const preloadPath = path.join(__dirname, "../preload/index.mjs");

  mainWindow = new BrowserWindow({
    width: 1480,
    height: 920,
    minWidth: 1100,
    minHeight: 720,
    show: false,
    title: PRODUCT_NAME,
    autoHideMenuBar: false,
    backgroundColor: "#f4efe4",
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      sandbox: false,
      nodeIntegration: false
    }
  });

  mainWindow.on("ready-to-show", () => mainWindow?.show());
  mainWindow.on("closed", () => {
    mainWindow = null;
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url);
    return { action: "deny" };
  });

  if (process.env.ELECTRON_RENDERER_URL) {
    void mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    void mainWindow.loadFile(path.join(__dirname, "../renderer/index.html"));
  }
}

function createApplicationMenu() {
  const template = [
    {
      label: "File",
      submenu: [
        {
          label: "Settings",
          accelerator: "CmdOrCtrl+,",
          click: () => {
            mainWindow?.webContents.send("app:open-settings");
          }
        },
        { type: "separator" as const },
        { role: "quit" as const }
      ]
    },
    {
      label: "View",
      submenu: [
        {
          label: "Reload",
          accelerator: "CmdOrCtrl+R",
          click: () => {
            mainWindow?.webContents.send("app:reload-workspace");
          }
        },
        { role: "forceReload" as const },
        { role: "toggleDevTools" as const },
        { type: "separator" as const },
        { role: "resetZoom" as const },
        { role: "zoomIn" as const },
        { role: "zoomOut" as const },
        { type: "separator" as const },
        { role: "togglefullscreen" as const }
      ]
    },
    {
      label: "Window",
      submenu: [{ role: "minimize" as const }, { role: "close" as const }]
    }
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function registerIpc() {
  ipcMain.handle("desktop:get-runtime-info", () => getRuntimeInfo());
  ipcMain.handle("desktop:get-settings", () => getSettings());
  ipcMain.handle("desktop:set-settings", (_event, update: Partial<DesktopSettings>) => {
    const nextSettings = normalizeSettings({
      ...getSettings(),
      ...update
    });
    settingsStore.set("apiBaseUrl", nextSettings.apiBaseUrl);
    settingsStore.set("language", nextSettings.language);
    return nextSettings;
  });
}

app.whenReady().then(() => {
  settingsStore = new Store<DesktopSettings>({
    defaults: getDefaultSettings(),
    name: "desktop-settings"
  });

  app.setName(PRODUCT_NAME);
  createApplicationMenu();
  registerIpc();
  createWindow();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

app.on("activate", () => {
  if (!mainWindow) {
    createWindow();
    return;
  }
  mainWindow.show();
  mainWindow.focus();
});
