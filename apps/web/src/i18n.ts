import type { LanguageMode, RuntimeInfo } from "./platform";

export type AppCopy = {
  locale: string;
  heroEyebrow: string;
  heroTitle: string;
  heroSummary: string;
  settingsButton: string;
  metrics: {
    changes: string;
    projects: string;
    snapshots: string;
  };
  panels: {
    changesSubtitle: string;
    changesTitle: string;
    classGraphTitle: string;
    importProjectSubtitle: string;
    importProjectTitle: string;
    indexControlSubtitle: string;
    indexControlTitle: string;
    methodGraphTitle: string;
    projectsTitle: string;
    reviewExportSubtitle: string;
    reviewExportTitle: string;
    reviewNotesSubtitle: string;
    reviewNotesTitle: string;
    selectionSubtitle: string;
    selectionTitle: string;
    snapshotsSubtitle: string;
    snapshotsTitle: string;
  };
  states: {
    deletingProject: string;
    exportingMarkdown: string;
    importing: string;
    indexing: string;
    loadingProjects: string;
    reviewing: string;
  };
  buttons: {
    back: string;
    cancel: string;
    close: string;
    commitRange: string;
    copyMarkdown: string;
    deleteProject: string;
    expand: string;
    gitAuto: string;
    importProject: string;
    incremental: string;
    full: string;
    manual: string;
    exportMarkdown: string;
    runReview: string;
    runIndex: string;
    saveSettings: string;
    useDefault: string;
  };
  fields: {
    apiBaseUrl: string;
    baseCommit: string;
    changedFiles: string;
    incrementalSource: string;
    language: string;
    name: string;
    reviewSource: string;
    rootPath: string;
    runtime: string;
    targetCommit: string;
  };
  placeholders: {
    apiBaseUrl: string;
    baseCommit: string;
    changedFiles: string;
    name: string;
    rootPath: string;
    targetCommit: string;
  };
  copy: {
    apiBaseUrlHintDesktop: string;
    apiBaseUrlHintWeb: string;
    changesEmptyBody: string;
    changesEmptyTitle: string;
    fullIndexHint: string;
    graphLoadingBody: string;
    incrementalGitHint: string;
    incrementalManualHint: string;
    languageHint: string;
    modeDesktop: string;
    modeWeb: string;
    noMethodChange: string;
    noSnapshot: string;
    noTypeChange: string;
    noWorkspaceSelected: string;
    noWorkspaceSelectedBody: string;
    projectsSubtitle: string;
    reviewNotes: string[];
    snapshotsEmptyBody: string;
    snapshotsEmptyTitle: string;
    classGraphEmptyBody: string;
    classGraphEmptyTitle: string;
    methodGraphEmptyBody: string;
    methodGraphEmptyTitle: string;
    projectsEmptyBody: string;
    projectsEmptyTitle: string;
    reviewCommitRangeHint: string;
    reviewExportEmptyBody: string;
    reviewExportEmptyTitle: string;
    reviewGitHint: string;
    reviewManualHint: string;
    reviewMarkdownLabel: string;
    reviewTargetsLabel: string;
    settingsSubtitle: string;
  };
  messages: {
    clipboardUnavailable: string;
    classExpanded: (name: string, count: number) => string;
    confirmDeleteProject: (name: string) => string;
    indexFinished: (name: string, typeCount: number, methodCount: number, relationCount: number) => string;
    markdownCopied: (fileName: string) => string;
    markdownExported: (fileName: string) => string;
    projectDeleted: (name: string) => string;
    projectReady: (name: string) => string;
    reviewFinished: (riskLevel: string, targetCount: number) => string;
    settingsSaved: string;
    snapshotLoaded: (snapshotId: string, classCount: number, relationCount: number, changeCount: number) => string;
    selectProjectBeforeIndex: string;
    selectProjectBeforeReview: string;
    selectSnapshotBeforeReview: string;
    unsupportedProjectLanguage: string;
    unexpectedError: string;
  };
  subtitles: {
    classGraph: (projectName: string | null) => string;
    methodGraph: (className: string | null) => string;
    projects: (loading: boolean) => string;
  };
  graph: {
    clearScope: string;
    focused: (name: string) => string;
    instructions: string;
    isolateHint: string;
    reset: string;
    showAll: string;
    showPreview: string;
    scopeConnected: string;
    scopeDirect: string;
    viewportEmptyBody: string;
    viewportEmptyTitle: string;
    visible: (visibleCount: number, totalCount: number) => string;
    preview: (visibleCount: number, hiddenCount: number) => string;
    zoom: (zoomPercent: number) => string;
    zoomIn: string;
    zoomOut: string;
  };
  settings: {
    title: string;
    english: string;
    chinese: string;
  };
};

const EN_COPY: AppCopy = {
  locale: "en-US",
  heroEyebrow: "Java Review Graph",
  heroTitle: "Review structural change, inspect graph clusters, and open methods on demand.",
  heroSummary:
    "This desktop-ready review surface works with the local Spring API and SQLite snapshots. Import a Java repo, index it, inspect the class graph, and expand one class at a time into its internal method graph.",
  settingsButton: "Settings",
  metrics: {
    changes: "Changes",
    projects: "Projects",
    snapshots: "Snapshots"
  },
  panels: {
    changesSubtitle: "Persisted symbol changes for the selected snapshot.",
    changesTitle: "Changes",
    classGraphTitle: "Class Graph",
    importProjectSubtitle: "Register a local repository path in SQLite.",
    importProjectTitle: "Import Project",
    indexControlSubtitle: "Create full or incremental snapshots.",
    indexControlTitle: "Index Control",
    methodGraphTitle: "Method Graph",
    projectsTitle: "Projects",
    reviewExportSubtitle: "Run change-set review on the selected snapshot, then preview, copy, or export a Markdown summary.",
    reviewExportTitle: "Change-Set Review",
    reviewNotesSubtitle: "Current scope and implementation limits.",
    reviewNotesTitle: "Review Notes",
    selectionSubtitle: "Current class or method focus.",
    selectionTitle: "Selection",
    snapshotsSubtitle: "Switch review context across saved index runs.",
    snapshotsTitle: "Snapshots"
  },
  states: {
    deletingProject: "Deleting...",
    exportingMarkdown: "Exporting...",
    importing: "Importing...",
    indexing: "Indexing...",
    loadingProjects: "Loading imported projects...",
    reviewing: "Reviewing..."
  },
  buttons: {
    back: "Back",
    cancel: "Cancel",
    close: "Close",
    commitRange: "Commit Range",
    copyMarkdown: "Copy Markdown",
    deleteProject: "Delete Project",
    expand: "Expand",
    gitAuto: "Git Auto",
    importProject: "Import Project",
    incremental: "Incremental",
    full: "Full",
    manual: "Manual",
    exportMarkdown: "Export Markdown",
    runReview: "Run Review",
    runIndex: "Run Index",
    saveSettings: "Save Settings",
    useDefault: "Use Default"
  },
  fields: {
    apiBaseUrl: "Backend API Base URL",
    baseCommit: "Base Commit",
    changedFiles: "Changed Files",
    incrementalSource: "Change Source",
    language: "Language",
    name: "Name",
    reviewSource: "Review Source",
    rootPath: "Root Path",
    runtime: "Runtime",
    targetCommit: "Target Commit"
  },
  placeholders: {
    apiBaseUrl: "http://127.0.0.1:8080",
    baseCommit: "HEAD~1",
    changedFiles: "src/main/java/com/example/user/UserService.java",
    name: "demo-project",
    rootPath: "C:/repo/demo-project",
    targetCommit: "HEAD"
  },
  copy: {
    apiBaseUrlHintDesktop: "Leave blank to use the desktop default `http://127.0.0.1:8080`.",
    apiBaseUrlHintWeb: "Leave blank to use the Vite `/api` proxy in browser mode.",
    changesEmptyBody: "The first snapshot often starts with added symbols only after indexing.",
    changesEmptyTitle: "No change records",
    fullIndexHint: "Full mode rescans all discovered Java source roots for the selected project.",
    graphLoadingBody: "Loading the graph renderer and layout engine...",
    incrementalGitHint:
      "Git Auto collects changed paths from the latest snapshot commit to the current workspace, and falls back to current working tree changes when the latest snapshot has no committed Git base.",
    incrementalManualHint: "Manual mode accepts one relative file path per line.",
    languageHint: "Switch application chrome between English and Chinese instantly.",
    modeDesktop: "Desktop",
    modeWeb: "Web",
    noMethodChange: "No explicit change record for this method in the selected snapshot.",
    noSnapshot: "No snapshot exists for this project yet. Run a full index to build the first class graph.",
    noTypeChange: "Select a method below for a narrower review target.",
    noWorkspaceSelected: "Nothing selected",
    noWorkspaceSelectedBody: "Choose a class to inspect method-level relations, then choose a method for detail focus.",
    projectsSubtitle: "Choose the active review target.",
    reviewNotes: [
      "Class graph shows in-project extends, implements, and uses edges.",
      "Both graph canvases are pan-and-zoom views, so you can inspect one cluster instead of forcing the whole graph into a single frame.",
      "Method graph currently shows local same-class calls edges.",
      "Incremental runs reuse unchanged snapshot data and mark one-hop impacted neighbors, including deleted-edge cases."
    ],
    snapshotsEmptyBody: "Run an index to persist the first review snapshot.",
    snapshotsEmptyTitle: "No snapshots",
    classGraphEmptyBody: "Import a project and run an index to populate class nodes.",
    classGraphEmptyTitle: "No class graph yet",
    methodGraphEmptyBody: "Method nodes appear after selecting a class from the class graph.",
    methodGraphEmptyTitle: "No method graph loaded",
    projectsEmptyBody: "Import a local Java repository to create the first review workspace.",
    projectsEmptyTitle: "No projects yet",
    reviewCommitRangeHint: "Commit Range compares two explicit Git commits and ignores current workspace-only edits.",
    reviewExportEmptyBody: "Run a change-set review to inspect risk, targets, and export-ready Markdown.",
    reviewExportEmptyTitle: "No review report yet",
    reviewGitHint:
      "Git Auto reuses the selected snapshot Git base and includes current workspace changes when they exist.",
    reviewManualHint: "Manual review accepts one relative file path per line.",
    reviewMarkdownLabel: "Markdown Preview",
    reviewTargetsLabel: "Priority Targets",
    settingsSubtitle: "Desktop preferences and language controls."
  },
  messages: {
    clipboardUnavailable: "Clipboard is unavailable in this environment.",
    classExpanded: (name, count) => `Expanded ${name} with ${count} methods.`,
    confirmDeleteProject: (name) => `Delete project ${name}? This removes its snapshots and stored review data.`,
    indexFinished: (name, typeCount, methodCount, relationCount) =>
      `Index finished for ${name}: ${typeCount} classes, ${methodCount} methods, ${relationCount} relations.`,
    markdownCopied: (fileName) => `Copied Markdown report: ${fileName}.`,
    markdownExported: (fileName) => `Markdown report ready: ${fileName}.`,
    projectDeleted: (name) => `Deleted project ${name}.`,
    projectReady: (name) => `Project ${name} is ready. Run an index to populate the first snapshot.`,
    reviewFinished: (riskLevel, targetCount) => `Change-set review finished with ${riskLevel} risk and ${targetCount} priority target(s).`,
    settingsSaved: "Settings saved.",
    snapshotLoaded: (snapshotId, classCount, relationCount, changeCount) =>
      `Loaded snapshot ${snapshotId} with ${classCount} classes, ${relationCount} class relations, and ${changeCount} change records.`,
    selectProjectBeforeIndex: "Select a project before starting an index run.",
    selectProjectBeforeReview: "Select a project before running change-set review.",
    selectSnapshotBeforeReview: "Select a snapshot before running change-set review.",
    unsupportedProjectLanguage: "Unsupported project language. Only Java projects can be imported.",
    unexpectedError: "Unexpected error."
  },
  subtitles: {
    classGraph: (projectName) =>
      projectName
        ? `Knowledge-style scatter view for ${projectName}. Drag the canvas, zoom in, and open one class when you want method detail.`
        : "Choose a project to load the current class graph.",
    methodGraph: (className) =>
      className
        ? `Method-level scatter view for ${className}. Pan and zoom instead of fitting every method into one frame.`
        : "Select a class node above to expand its internal methods.",
    projects: (loading) => (loading ? "Loading imported projects..." : "Choose the active review target.")
  },
  graph: {
    clearScope: "Show all",
    focused: (name) => `Focused ${name}`,
    instructions: "Hover or focus graph | Scroll to zoom | Drag to explore | Double-click to isolate",
    isolateHint: "Double-click a node to isolate related nodes",
    reset: "Reset",
    showAll: "Show full graph",
    showPreview: "Preview mode",
    scopeConnected: "Direct + indirect",
    scopeDirect: "Direct only",
    viewportEmptyBody: "Drag back toward the cluster or reset the camera.",
    viewportEmptyTitle: "No nodes in this viewport.",
    visible: (visibleCount, totalCount) => `Visible ${visibleCount}/${totalCount}`,
    preview: (visibleCount, hiddenCount) => `Preview ${visibleCount} nodes | ${hiddenCount} hidden`,
    zoom: (zoomPercent) => `Zoom ${zoomPercent}%`,
    zoomIn: "Zoom in",
    zoomOut: "Zoom out"
  },
  settings: {
    title: "Settings",
    english: "English",
    chinese: "Chinese"
  }
};

const ZH_COPY = {
  locale: "zh-CN",
  heroEyebrow: "Java Review Graph",
  heroTitle: "围绕结构变化做评审，先看类关系，再按需展开方法。",
  heroSummary:
    "这个评审界面已经适配桌面端，连接本地 Spring API 和 SQLite 快照。你可以导入 Java 仓库、执行索引、查看类级知识图谱，并按需展开单个类的内部方法关系。",
  settingsButton: "设置",
  metrics: {
    changes: "变更数",
    projects: "项目数",
    snapshots: "快照数"
  },
  panels: {
    changesSubtitle: "当前快照中持久化保存的符号变更记录。",
    changesTitle: "变更列表",
    classGraphTitle: "类图谱",
    importProjectSubtitle: "把本地仓库路径登记到 SQLite 中。",
    importProjectTitle: "导入项目",
    indexControlSubtitle: "创建全量或增量快照。",
    indexControlTitle: "索引控制",
    methodGraphTitle: "方法图谱",
    projectsTitle: "项目列表",
    reviewNotesSubtitle: "当前实现范围和已知限制。",
    reviewNotesTitle: "评审说明",
    selectionSubtitle: "当前选中的类或方法。",
    selectionTitle: "当前选择",
    snapshotsSubtitle: "在不同索引快照之间切换评审上下文。",
    snapshotsTitle: "快照列表"
  },
  states: {
    importing: "导入中...",
    indexing: "索引中...",
    loadingProjects: "正在加载已导入项目..."
  },
  buttons: {
    cancel: "取消",
    close: "关闭",
    commitRange: "提交区间",
    gitAuto: "Git 自动",
    importProject: "导入项目",
    incremental: "增量",
    full: "全量",
    manual: "手工输入",
    runIndex: "执行索引",
    saveSettings: "保存设置",
    useDefault: "恢复默认"
  },
  fields: {
    apiBaseUrl: "后端 API 地址",
    baseCommit: "起始提交",
    changedFiles: "变更文件",
    incrementalSource: "变更来源",
    language: "界面语言",
    name: "项目名称",
    reviewSource: "Review 来源",
    rootPath: "项目根路径",
    runtime: "运行模式",
    targetCommit: "目标提交"
  },
  placeholders: {
    apiBaseUrl: "http://127.0.0.1:8080",
    baseCommit: "HEAD~1",
    changedFiles: "src/main/java/com/example/user/UserService.java",
    name: "demo-project",
    rootPath: "C:/repo/demo-project",
    targetCommit: "HEAD"
  },
  copy: {
    apiBaseUrlHintDesktop: "留空时使用桌面端默认地址 `http://127.0.0.1:8080`。",
    apiBaseUrlHintWeb: "浏览器模式下留空时使用 Vite 的 `/api` 代理。",
    changesEmptyBody: "第一份快照通常会在索引完成后，才出现新增符号等变更记录。",
    changesEmptyTitle: "还没有变更记录",
    fullIndexHint: "全量模式会重新扫描当前项目下识别到的全部 Java 源码目录。",
    graphLoadingBody: "正在加载图谱渲染器和布局引擎...",
    incrementalGitHint:
      "Git 自动会从最新快照对应的 commit 到当前工作区收集变更路径；如果最新快照没有可用的提交基线，则退回为当前工作区变更。",
    incrementalManualHint: "手工模式按行输入相对路径，每行一个文件。",
    languageHint: "界面文案可在中文和英文之间即时切换。",
    modeDesktop: "桌面端",
    modeWeb: "浏览器",
    noMethodChange: "当前快照里没有这个方法的显式变更记录。",
    noSnapshot: "这个项目还没有快照，请先执行一次全量索引生成第一版类图谱。",
    noTypeChange: "可以继续在下方选择一个方法，把评审范围收窄到更细粒度。",
    noWorkspaceSelected: "当前没有选中对象",
    noWorkspaceSelectedBody: "先选择一个类查看方法级关系，再选择具体方法查看细节。",
    projectsSubtitle: "选择当前要评审的项目。",
    reviewNotes: [
      "类图谱展示项目内的 extends、implements 和 uses 关系。",
      "两个图谱画布都支持拖动和缩放，不再强制把整个图塞进一个固定视口。",
      "方法图谱当前只展示同一个类内部的 calls 调用关系。",
      "增量索引会复用未变化的快照数据，并对变更或删除邻居做一跳 impacted 标记。"
    ],
    snapshotsEmptyBody: "先执行一次索引，保存第一份评审快照。",
    snapshotsEmptyTitle: "还没有快照",
    classGraphEmptyBody: "导入项目并执行索引后，这里会生成类节点。",
    classGraphEmptyTitle: "还没有类图谱",
    methodGraphEmptyBody: "先在上方类图谱中选择一个类，这里才会展开方法节点。",
    methodGraphEmptyTitle: "还没有方法图谱",
    projectsEmptyBody: "导入一个本地 Java 仓库后，这里会建立第一份评审工作区。",
    projectsEmptyTitle: "还没有项目",
    reviewCommitRangeHint: "提交区间模式会对比两个明确的 Git 提交，不会带入当前仅存在于工作区的改动。",
    reviewGitHint: "Git 自动会复用所选快照的 Git 基线，并在存在时带上当前工作区改动。",
    reviewManualHint: "手工评审按行输入相对路径，每行一个文件。",
    settingsSubtitle: "桌面端偏好项和语言切换。"
  },
  messages: {
    classExpanded: (name, count) => `已展开 ${name}，共 ${count} 个方法。`,
    indexFinished: (name, typeCount, methodCount, relationCount) =>
      `${name} 索引完成：${typeCount} 个类，${methodCount} 个方法，${relationCount} 条关系。`,
    projectReady: (name) => `项目 ${name} 已准备完成，请执行索引生成第一份快照。`,
    settingsSaved: "设置已保存。",
    snapshotLoaded: (snapshotId, classCount, relationCount, changeCount) =>
      `已加载快照 ${snapshotId}：${classCount} 个类，${relationCount} 条类关系，${changeCount} 条变更记录。`,
    selectProjectBeforeIndex: "请先选择一个项目，再开始执行索引。",
    unexpectedError: "出现了未预期的错误。"
  },
  subtitles: {
    classGraph: (projectName) =>
      projectName
        ? `${projectName} 的类级知识图谱。可以拖动画布、滚轮缩放，并按需点开单个类查看方法细节。`
        : "先选择一个项目，再加载当前类图谱。",
    methodGraph: (className) =>
      className
        ? `${className} 的方法级散点图谱。通过平移和缩放聚焦局部，而不是把所有方法压缩进同一屏。`
        : "在上方类图谱里选择一个类后，这里会展开内部方法关系。",
    projects: (loading) => (loading ? "正在加载已导入项目..." : "选择当前要评审的项目。")
  },
  graph: {
    clearScope: "显示全部",
    focused: (name) => `聚焦 ${name}`,
    instructions: "鼠标悬停或聚焦画布 | 滚轮缩放 | 拖动查看局部 | 双击节点隔离关联图",
    isolateHint: "双击节点后只查看与它相关的节点和关系",
    reset: "重置视图",
    showAll: "显示全量图",
    showPreview: "预览模式",
    scopeConnected: "直接 + 间接",
    scopeDirect: "仅直接关系",
    viewportEmptyBody: "把画布拖回主聚簇附近，或直接重置视角。",
    viewportEmptyTitle: "当前视口没有节点。",
    visible: (visibleCount, totalCount) => `可见 ${visibleCount}/${totalCount}`,
    preview: (visibleCount, hiddenCount) => `预览 ${visibleCount} 个节点 | 隐藏 ${hiddenCount} 个`,
    zoom: (zoomPercent) => `缩放 ${zoomPercent}%`,
    zoomIn: "放大",
    zoomOut: "缩小"
  },
  settings: {
    title: "设置",
    english: "英文",
    chinese: "中文"
  }
} as AppCopy;

ZH_COPY.messages.unsupportedProjectLanguage = "暂不支持导入该语言项目，目前只支持 Java 项目。";

ZH_COPY.states.deletingProject = "删除中...";
ZH_COPY.states.exportingMarkdown = "导出中...";
ZH_COPY.states.reviewing = "分析中...";
ZH_COPY.buttons.back = "返回";
ZH_COPY.buttons.copyMarkdown = "复制 Markdown";
ZH_COPY.buttons.deleteProject = "删除项目";
ZH_COPY.buttons.expand = "展开";
ZH_COPY.buttons.exportMarkdown = "导出 Markdown";
ZH_COPY.buttons.runReview = "执行 Review";
ZH_COPY.fields.reviewSource = "Review 来源";
ZH_COPY.panels.reviewExportTitle = "Change-Set Review";
ZH_COPY.panels.reviewExportSubtitle = "基于当前快照执行 change-set review，并预览、复制或导出 Markdown 报告。";
ZH_COPY.copy.reviewExportEmptyTitle = "还没有报告";
ZH_COPY.copy.reviewExportEmptyBody = "执行一次 change-set review，这里会显示风险、优先目标和 Markdown 预览。";
ZH_COPY.copy.reviewMarkdownLabel = "Markdown 预览";
ZH_COPY.copy.reviewTargetsLabel = "优先 Review 目标";
ZH_COPY.messages.clipboardUnavailable = "当前环境无法访问剪贴板。";
ZH_COPY.messages.confirmDeleteProject = (name) => `确认删除项目 ${name}？该项目的快照和已保存评审数据会一起删除。`;
ZH_COPY.messages.markdownCopied = (fileName) => `Markdown 报告已复制到剪贴板：${fileName}。`;
ZH_COPY.messages.markdownExported = (fileName) => `Markdown 报告已准备完成：${fileName}。`;
ZH_COPY.messages.projectDeleted = (name) => `已删除项目 ${name}。`;
ZH_COPY.messages.reviewFinished = (riskLevel, targetCount) => `change-set review 已完成，风险等级为 ${riskLevel}，优先目标 ${targetCount} 个。`;
ZH_COPY.messages.selectProjectBeforeReview = "请先选择一个项目再执行 change-set review。";
ZH_COPY.messages.selectSnapshotBeforeReview = "请先选择一个快照再执行 change-set review。";

export function getCopy(language: LanguageMode) {
  return language === "zh" ? ZH_COPY : EN_COPY;
}

export function formatStatusLabel(status: string, language: LanguageMode) {
  const normalizedStatus = status.toLowerCase();
  if (language === "zh") {
    switch (normalizedStatus) {
      case "added":
        return "新增";
      case "modified_api":
        return "接口变更";
      case "modified_impl":
        return "实现变更";
      case "deleted":
        return "删除";
      case "impacted":
        return "受影响";
      default:
        return "未变更";
    }
  }

  switch (normalizedStatus) {
    case "added":
      return "Added";
    case "modified_api":
      return "API Changed";
    case "modified_impl":
      return "Impl Changed";
    case "deleted":
      return "Deleted";
    case "impacted":
      return "Impacted";
    default:
      return "Unchanged";
  }
}

export function formatKindLabel(kind: string, language: LanguageMode) {
  const normalizedKind = kind.toUpperCase();
  if (language === "zh") {
    switch (normalizedKind) {
      case "CLASS":
        return "类";
      case "INTERFACE":
        return "接口";
      case "ENUM":
        return "枚举";
      case "RECORD":
        return "记录";
      case "ANNOTATION":
        return "注解";
      case "METHOD":
        return "方法";
      case "CONSTRUCTOR":
        return "构造器";
      default:
        return kind;
    }
  }

  switch (normalizedKind) {
    case "CLASS":
      return "Class";
    case "INTERFACE":
      return "Interface";
    case "ENUM":
      return "Enum";
    case "RECORD":
      return "Record";
    case "ANNOTATION":
      return "Annotation";
    case "METHOD":
      return "Method";
    case "CONSTRUCTOR":
      return "Constructor";
    default:
      return kind;
  }
}

export function formatEdgeTypeLabel(edgeType: string, language: LanguageMode) {
  const normalizedEdgeType = edgeType.toLowerCase();
  if (language === "zh") {
    switch (normalizedEdgeType) {
      case "extends":
        return "继承";
      case "implements":
        return "实现";
      case "uses_type":
        return "使用";
      case "calls":
        return "调用";
      default:
        return edgeType.replace(/_/g, " ");
    }
  }

  switch (normalizedEdgeType) {
    case "uses_type":
      return "uses";
    default:
      return edgeType.replace(/_/g, " ");
  }
}

export function formatRuntimeLabel(runtime: RuntimeInfo, language: LanguageMode) {
  if (runtime.mode === "desktop") {
    return language === "zh" ? "桌面端 Electron" : "Electron Desktop";
  }
  return language === "zh" ? "浏览器 / Vite" : "Browser / Vite";
}
