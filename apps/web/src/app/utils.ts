import type { ChangeSetReviewMarkdownReport, ClassGraph, GraphNode, ProjectSnapshot, SymbolChange } from "../api/client";
import { formatEdgeTypeLabel, formatStatusLabel, getCopy } from "../i18n";
import type { LanguageMode } from "../platform";
import type { ClassGraphDisplayMode, SnapshotDiagnosticsCopy, SnapshotGroup } from "./view-model";

const PREFERRED_CHANGE_FILTER_ORDER = ["added", "modified_api", "modified_impl", "impacted", "deleted", "unchanged"] as const;

export type ChangeFilterOption = {
  count: number;
  key: string;
  label: string;
  statusClass: string | null;
};

export function filterClassGraph(graph: ClassGraph | null, searchQuery: string, displayMode: ClassGraphDisplayMode) {
  if (!graph) {
    return null;
  }

  const normalizedTerms = searchQuery
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean);
  const visibleNodes = graph.nodes.filter((node) => {
    if (displayMode === "incremental" && !isIncrementalClassNode(node)) {
      return false;
    }
    if (normalizedTerms.length === 0) {
      return true;
    }

    const haystack = `${node.name} ${node.qualifiedName}`.toLowerCase();
    return normalizedTerms.every((term) => haystack.includes(term));
  });

  const visibleNodeIds = new Set(visibleNodes.map((node) => node.id));

  return {
    ...graph,
    edges: graph.edges.filter((edge) => visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target)),
    nodes: visibleNodes
  };
}

export function getClassGraphSearchLabel(language: LanguageMode) {
  return language === "zh" ? "搜索节点" : "Search nodes";
}

export function getClassGraphSearchPlaceholder(language: LanguageMode) {
  return language === "zh" ? "按类名或限定名模糊匹配" : "Fuzzy match by class name or qualified name";
}

export function getClassGraphDisplayLabel(language: LanguageMode) {
  return language === "zh" ? "显示范围" : "Display";
}

export function getClassGraphOverlayToggleLabel(language: LanguageMode, isCollapsed: boolean) {
  if (language === "zh") {
    return isCollapsed ? "展开筛选" : "收起筛选";
  }
  return isCollapsed ? "Show filters" : "Hide filters";
}

export function formatClassGraphEdgeType(edgeType: string, language: LanguageMode) {
  if (edgeType.toLowerCase() === "uses_type") {
    return language === "zh" ? "依赖" : "dependency";
  }
  return formatEdgeTypeLabel(edgeType, language);
}

export function resolveClassGraphEmptyState(
  language: LanguageMode,
  graph: ClassGraph | null,
  searchQuery: string,
  displayMode: ClassGraphDisplayMode
) {
  if (!graph || graph.nodes.length === 0) {
    return {
      body: getCopy(language).copy.classGraphEmptyBody,
      title: getCopy(language).copy.classGraphEmptyTitle
    };
  }

  if (searchQuery.trim().length > 0) {
    return language === "zh"
      ? {
          title: "没有匹配的类",
          body: "当前搜索条件下没有匹配到类。可尝试缩短关键词，或切换回全量显示。"
        }
      : {
          title: "No matching classes",
          body: "No classes match the current search. Try a shorter keyword or switch back to full display."
        };
  }

  if (displayMode === "incremental") {
    return language === "zh"
      ? {
          title: "没有增量类",
          body: "当前快照里没有新增或变化的类。可切换回全量显示查看完整类图谱。"
        }
      : {
          title: "No incremental classes",
          body: "This snapshot has no added or changed classes. Switch back to full display to see the complete graph."
        };
  }

  return {
    body: getCopy(language).copy.classGraphEmptyBody,
    title: getCopy(language).copy.classGraphEmptyTitle
  };
}

export function toMessage(error: unknown, fallbackMessage: string, codeMessages?: Record<string, string>) {
  if (error instanceof Error) {
    const errorCode =
      typeof error === "object" && error !== null && "code" in error && typeof error.code === "string" ? error.code : undefined;
    if (errorCode && codeMessages?.[errorCode]) {
      return codeMessages[errorCode];
    }
    return error.message;
  }
  return fallbackMessage;
}

export function shortId(value: string) {
  return value.slice(0, 8);
}

export function resolveSnapshotDisplayName(snapshot: ProjectSnapshot) {
  return snapshot.displayName === snapshot.id ? shortId(snapshot.displayName) : snapshot.displayName;
}

export function resolveContextMenuPosition(clientX: number, clientY: number) {
  const menuWidth = 196;
  const menuHeight = 120;
  const viewportPadding = 12;
  const maxX = Math.max(viewportPadding, window.innerWidth - menuWidth - viewportPadding);
  const maxY = Math.max(viewportPadding, window.innerHeight - menuHeight - viewportPadding);

  return {
    x: Math.min(clientX, maxX),
    y: Math.min(clientY, maxY)
  };
}

export function compactSymbolKey(symbolKey: string) {
  const methodSeparatorIndex = symbolKey.indexOf("#");
  if (methodSeparatorIndex >= 0) {
    return symbolKey.slice(methodSeparatorIndex + 1);
  }
  const typeSeparatorIndex = symbolKey.lastIndexOf(":");
  return typeSeparatorIndex >= 0 ? symbolKey.slice(typeSeparatorIndex + 1) : symbolKey;
}

export function normalizeRiskStatusClass(riskLevel: string) {
  switch (riskLevel.toLowerCase()) {
    case "high":
      return "status-modified_api";
    case "medium":
      return "status-impacted";
    default:
      return "status-added";
  }
}

export function downloadMarkdownReport(report: ChangeSetReviewMarkdownReport) {
  const blob = new Blob([report.markdown], { type: "text/markdown;charset=utf-8" });
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = report.fileName;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

export async function copyTextToClipboard(text: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "true");
  textarea.style.position = "fixed";
  textarea.style.top = "0";
  textarea.style.left = "0";
  textarea.style.opacity = "0";
  textarea.style.pointerEvents = "none";
  document.body.append(textarea);
  textarea.select();
  textarea.setSelectionRange(0, textarea.value.length);

  try {
    if (!document.execCommand("copy")) {
      throw new Error("Clipboard API is unavailable.");
    }
  } finally {
    textarea.remove();
  }
}

export function groupSymbolChanges(changes: SymbolChange[]) {
  const groups = new Map<string, SymbolChange[]>();

  for (const change of changes) {
    const group = groups.get(change.changeType);
    if (group) {
      group.push(change);
      continue;
    }
    groups.set(change.changeType, [change]);
  }

  return groups;
}

export function buildChangeFilterOptions(
  changes: SymbolChange[],
  changeGroups: Map<string, SymbolChange[]>,
  language: LanguageMode
): ChangeFilterOption[] {
  const options: ChangeFilterOption[] = [
    {
      count: changes.length,
      key: "all",
      label: getAllChangeFilterLabel(language),
      statusClass: null
    }
  ];
  const seenKeys = new Set<string>();

  for (const key of PREFERRED_CHANGE_FILTER_ORDER) {
    const groupedChanges = changeGroups.get(key);
    if (!groupedChanges || groupedChanges.length === 0) {
      continue;
    }
    options.push({
      count: groupedChanges.length,
      key,
      label: formatStatusLabel(key, language),
      statusClass: key
    });
    seenKeys.add(key);
  }

  for (const [key, groupedChanges] of changeGroups.entries()) {
    if (seenKeys.has(key) || groupedChanges.length === 0) {
      continue;
    }
    options.push({
      count: groupedChanges.length,
      key,
      label: formatStatusLabel(key, language),
      statusClass: key
    });
  }

  return options;
}

export function getAllChangeFilterLabel(language: LanguageMode) {
  return language === "zh" ? "全部" : "All";
}

export function formatChangePaginationLabel(
  language: LanguageMode,
  filterLabel: string,
  filteredCount: number,
  currentPage: number,
  pageCount: number
) {
  if (language === "zh") {
    return `${filterLabel} · 共 ${filteredCount} 项 · 第 ${currentPage}/${pageCount} 页`;
  }
  return `${filterLabel} · ${filteredCount} items · Page ${currentPage}/${pageCount}`;
}

export function parseChangedFilesText(changedFilesText: string) {
  return changedFilesText
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function buildSnapshotGroups(snapshots: ProjectSnapshot[], language: LanguageMode) {
  const groups: SnapshotGroup[] = [];
  const groupsByKey = new Map<string, SnapshotGroup>();

  for (const snapshot of snapshots) {
    const normalizedMessage = snapshot.gitCommitMessage?.trim() || null;
    const groupKey = snapshot.gitCommit ? `commit:${snapshot.gitCommit}` : "uncommitted";
    const title = normalizedMessage ?? (snapshot.gitCommit ? shortId(snapshot.gitCommit) : getUncommittedSnapshotGroupLabel(language));
    const shortCommit = snapshot.gitCommit ? shortId(snapshot.gitCommit) : null;

    let group = groupsByKey.get(groupKey);
    if (!group) {
      group = {
        key: groupKey,
        shortCommit,
        snapshots: [],
        title
      };
      groupsByKey.set(groupKey, group);
      groups.push(group);
    }

    group.snapshots.push(snapshot);
  }

  return groups;
}

export function findSnapshotGroupKey(snapshots: ProjectSnapshot[], snapshotId: string) {
  const snapshot = snapshots.find((item) => item.id === snapshotId);
  if (!snapshot) {
    return null;
  }
  return snapshot.gitCommit ? `commit:${snapshot.gitCommit}` : "uncommitted";
}

export function getUncommittedSnapshotGroupLabel(language: LanguageMode) {
  return language === "zh" ? "未 commit" : "Uncommitted";
}

export function buildGraphSceneStorageKey(kind: "class" | "method", projectId: string, snapshotId: string, classId?: string) {
  return classId ? `${kind}:${projectId}:${snapshotId}:${classId}` : `${kind}:${projectId}:${snapshotId}`;
}

export function formatSnapshotModeLabel(mode: string | null, language: LanguageMode, copy: SnapshotDiagnosticsCopy) {
  const normalizedMode = mode?.toLowerCase();
  if (normalizedMode === "incremental") {
    return language === "zh" ? "增量" : "Incremental";
  }
  if (normalizedMode === "full") {
    return language === "zh" ? "全量" : "Full";
  }
  return copy.none;
}

export function formatSnapshotChangeSourceLabel(
  changeSource: string | null,
  language: LanguageMode,
  copy: SnapshotDiagnosticsCopy
) {
  const normalizedChangeSource = changeSource?.toLowerCase();
  if (normalizedChangeSource === "git") {
    return language === "zh" ? "Git 自动" : "Git Auto";
  }
  if (normalizedChangeSource === "manual") {
    return language === "zh" ? "手工输入" : "Manual";
  }
  return copy.none;
}

export function getSnapshotDiagnosticsCopy(language: LanguageMode): SnapshotDiagnosticsCopy {
  if (language === "zh") {
    return {
      baseSnapshot: "基线快照",
      changeSource: "变更来源",
      changedFiles: "变更文件",
      effectiveMode: "实际模式",
      emptyBody: "选择一个快照后，这里会显示该次索引的诊断信息。",
      emptyTitle: "还没有快照诊断",
      fallbackReason: "回退原因",
      gitCommit: "Git 提交",
      loadingBody: "正在加载当前快照的诊断元数据。",
      loadingTitle: "正在加载诊断",
      no: "否",
      none: "无",
      renamedPaths: "重命名 / 移动",
      rebuildPaths: "重建范围",
      removedPaths: "移除路径",
      requestedMode: "请求模式",
      subtitle: "查看这次索引的变更集合、重建范围和回退说明。",
      summary: "诊断摘要",
      title: "索引诊断",
      unavailableBody: "当前快照没有可用的诊断元数据，可能是旧版本快照。",
      unavailableTitle: "诊断不可用",
      workspaceChanges: "包含工作区改动",
      yes: "是"
    };
  }

  return {
    baseSnapshot: "Base Snapshot",
    changeSource: "Change Source",
    changedFiles: "Changed Files",
    effectiveMode: "Effective Mode",
    emptyBody: "Choose a snapshot to inspect its indexing diagnostics.",
    emptyTitle: "No diagnostics yet",
    fallbackReason: "Fallback Reason",
    gitCommit: "Git Commit",
    loadingBody: "Loading persisted diagnostics for the selected snapshot.",
    loadingTitle: "Loading diagnostics",
    no: "No",
    none: "None",
    renamedPaths: "Renamed / Moved",
    rebuildPaths: "Rebuild Scope",
    removedPaths: "Removed Paths",
    requestedMode: "Requested Mode",
    subtitle: "Inspect changed files, rebuild scope, and fallback details for the selected snapshot.",
    summary: "Summary",
    title: "Index Diagnostics",
    unavailableBody: "This snapshot does not have persisted diagnostics metadata yet.",
    unavailableTitle: "Diagnostics unavailable",
    workspaceChanges: "Workspace Changes",
    yes: "Yes"
  };
}

export function getSnapshotUiCopy(language: LanguageMode) {
  if (language === "zh") {
    return {
      cancel: "取消",
      confirmDelete: (name: string) => `确认删除快照 ${name}？该快照下保存的类图、关系和变更记录会一起删除。`,
      delete: "删除",
      deleted: (name: string) => `已删除快照 ${name}。`,
      deleting: "删除中...",
      namePlaceholder: "输入快照名称",
      nameRequired: "快照名称不能为空。",
      rename: "重命名",
      renamed: (name: string) => `已将快照重命名为 ${name}。`,
      save: "保存"
    };
  }

  return {
    cancel: "Cancel",
    confirmDelete: (name: string) => `Delete snapshot ${name}? Its stored graph, relations, and change records will be removed.`,
    delete: "Delete",
    deleted: (name: string) => `Deleted snapshot ${name}.`,
    deleting: "Deleting...",
    namePlaceholder: "Enter snapshot name",
    nameRequired: "Snapshot name must not be blank.",
    rename: "Rename",
    renamed: (name: string) => `Renamed snapshot to ${name}.`,
    save: "Save"
  };
}

export function getContextMenuCopy(language: LanguageMode) {
  if (language === "zh") {
    return {
      deleteProject: "删除项目",
      deleteSnapshot: "删除快照",
      renameSnapshot: "重命名快照"
    };
  }

  return {
    deleteProject: "Delete Project",
    deleteSnapshot: "Delete Snapshot",
    renameSnapshot: "Rename Snapshot"
  };
}

function isIncrementalClassNode(node: GraphNode) {
  return node.status.toLowerCase() !== "unchanged";
}
