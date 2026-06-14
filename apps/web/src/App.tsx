import { type FormEvent, lazy, type MouseEvent as ReactMouseEvent, Suspense, useEffect, useState } from "react";
import {
  compareSnapshots,
  exportChangeSetReviewMarkdown,
  type ClassGraph,
  type ChangeSetReviewMarkdownReport,
  type ChangeSetReviewPayload,
  type ChangeSetReviewResult,
  type SnapshotCompareResult,
  createProject,
  deleteProject,
  deleteSnapshot,
  getChanges,
  getClassGraph,
  getMethodGraph,
  getSnapshotDiagnostics,
  listProjects,
  listSnapshots,
  renameSnapshot,
  reviewChangeSet,
  setApiBaseUrl,
  triggerIndex,
  type GraphNode,
  type MethodGraph,
  type Project,
  type ProjectSnapshot,
  type SnapshotDiagnostics,
  type SymbolChange
} from "./api/client";
import {
  ChangeList,
  ChangeSetReviewPanel,
  ContextMenu,
  type ContextMenuItem,
  EmptyState,
  MetricBadge,
  Panel,
  SelectionCard,
  SnapshotHistoryList,
  SnapshotComparePanel,
  SnapshotDiagnosticsPanel
} from "./app/components";
import {
  type ClassGraphDisplayMode,
  type ContextMenuState,
  type ExpandedGraphView,
  type IndexChangeSource,
  type ReviewChangeSource
} from "./app/view-model";
import {
  buildGraphSceneStorageKey,
  buildSnapshotGroups,
  downloadMarkdownReport,
  filterClassGraph,
  findSnapshotGroupKey,
  formatClassGraphEdgeType,
  getClassGraphDisplayLabel,
  getClassGraphOverlayToggleLabel,
  getClassGraphSearchLabel,
  getClassGraphSearchPlaceholder,
  getContextMenuCopy,
  getSnapshotDiagnosticsCopy,
  getSnapshotUiCopy,
  parseChangedFilesText,
  resolveClassGraphEmptyState,
  resolveContextMenuPosition,
  resolveSnapshotDisplayName,
  shortId,
  toMessage
} from "./app/utils";
import { formatEdgeTypeLabel, formatKindLabel, formatStatusLabel, getCopy } from "./i18n";
import { SettingsDrawer } from "./SettingsDrawer";
import {
  defaultSettings,
  loadUiSettings,
  resolveRuntimeInfo,
  saveUiSettings,
  subscribeToOpenSettings,
  subscribeToReloadWorkspace,
  type LanguageMode,
  type UiSettings
} from "./platform";

const runtime = resolveRuntimeInfo();
const GraphCanvas = lazy(() => import("./graph/GraphCanvas").then((module) => ({ default: module.GraphCanvas })));

function GraphLoadingState({ title, body }: { title: string; body: string }) {
  return (
    <div className="graph-stage graph-loading-stage">
      <EmptyState title={title} body={body} />
    </div>
  );
}

function App() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [snapshots, setSnapshots] = useState<ProjectSnapshot[]>([]);
  const [selectedSnapshotId, setSelectedSnapshotId] = useState<string | null>(null);
  const [snapshotDiagnostics, setSnapshotDiagnostics] = useState<SnapshotDiagnostics | null>(null);
  const [collapsedSnapshotGroupKeys, setCollapsedSnapshotGroupKeys] = useState<string[]>([]);
  const [classGraph, setClassGraph] = useState<ClassGraph | null>(null);
  const [methodGraph, setMethodGraph] = useState<MethodGraph | null>(null);
  const [changes, setChanges] = useState<SymbolChange[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<string | null>(null);
  const [selectedMethodId, setSelectedMethodId] = useState<string | null>(null);
  const [importName, setImportName] = useState("java-review-graph");
  const [importRootPath, setImportRootPath] = useState("C:/Users/29768/Desktop/java-review-graph");
  const [indexMode, setIndexMode] = useState<"full" | "incremental">("full");
  const [indexChangeSource, setIndexChangeSource] = useState<IndexChangeSource>("git");
  const [reviewChangeSource, setReviewChangeSource] = useState<ReviewChangeSource>("git");
  const [classGraphDisplayMode, setClassGraphDisplayMode] = useState<ClassGraphDisplayMode>("full");
  const [classGraphSearchQuery, setClassGraphSearchQuery] = useState("");
  const [changedFilesText, setChangedFilesText] = useState("");
  const [reviewChangedFilesText, setReviewChangedFilesText] = useState("");
  const [reviewBaseCommit, setReviewBaseCommit] = useState("");
  const [reviewTargetCommit, setReviewTargetCommit] = useState("");
  const [reviewResult, setReviewResult] = useState<ChangeSetReviewResult | null>(null);
  const [reviewMarkdownReport, setReviewMarkdownReport] = useState<ChangeSetReviewMarkdownReport | null>(null);
  const [snapshotCompareBaseId, setSnapshotCompareBaseId] = useState("");
  const [snapshotCompareResult, setSnapshotCompareResult] = useState<SnapshotCompareResult | null>(null);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingWorkspace, setLoadingWorkspace] = useState(false);
  const [loadingSnapshotDiagnostics, setLoadingSnapshotDiagnostics] = useState(false);
  const [loadingSnapshotCompare, setLoadingSnapshotCompare] = useState(false);
  const [submittingImport, setSubmittingImport] = useState(false);
  const [submittingIndex, setSubmittingIndex] = useState(false);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [exportingReviewMarkdown, setExportingReviewMarkdown] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);
  const [deletingSnapshotId, setDeletingSnapshotId] = useState<string | null>(null);
  const [renamingSnapshotId, setRenamingSnapshotId] = useState<string | null>(null);
  const [snapshotNameDraft, setSnapshotNameDraft] = useState("");
  const [workspaceMessage, setWorkspaceMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [settings, setSettings] = useState<UiSettings>(() => defaultSettings(runtime));
  const [draftSettings, setDraftSettings] = useState<UiSettings>(() => defaultSettings(runtime));
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);
  const [expandedGraphView, setExpandedGraphView] = useState<ExpandedGraphView>(null);
  const [isClassGraphOverlayCollapsed, setIsClassGraphOverlayCollapsed] = useState(false);

  const copy = getCopy(settings.language);
  const selectedProject = projects.find((project) => project.id === selectedProjectId) ?? null;
  const selectedSnapshot = snapshots.find((snapshot) => snapshot.id === selectedSnapshotId) ?? null;
  const manualChangedFiles = parseChangedFilesText(changedFilesText);
  const reviewManualChangedFiles = parseChangedFilesText(reviewChangedFilesText);
  const snapshotGroups = buildSnapshotGroups(snapshots, settings.language);
  const filteredClassGraph = filterClassGraph(classGraph, classGraphSearchQuery, classGraphDisplayMode);
  const classNodes = filteredClassGraph?.nodes ?? [];
  const selectedClass = classNodes.find((node) => node.id === selectedClassId) ?? null;
  const methodNodes = methodGraph?.nodes ?? [];
  const selectedMethod = methodNodes.find((node) => node.id === selectedMethodId) ?? null;
  const selectedChange = changes.find((change) => change.symbolKey === selectedMethodId || change.symbolKey === selectedClassId) ?? null;
  const snapshotUiCopy = getSnapshotUiCopy(settings.language);
  const snapshotDiagnosticsCopy = getSnapshotDiagnosticsCopy(settings.language);
  const contextMenuCopy = getContextMenuCopy(settings.language);
  const classGraphSearchLabel = getClassGraphSearchLabel(settings.language);
  const classGraphSearchPlaceholder = getClassGraphSearchPlaceholder(settings.language);
  const classGraphDisplayLabel = getClassGraphDisplayLabel(settings.language);
  const classGraphSceneStorageKey =
    selectedProjectId && classGraph ? buildGraphSceneStorageKey("class", selectedProjectId, classGraph.snapshotId) : null;
  const methodGraphSceneStorageKey =
    selectedProjectId && methodGraph
      ? buildGraphSceneStorageKey("method", selectedProjectId, methodGraph.snapshotId, methodGraph.classId)
      : null;
  const classGraphEmptyState = resolveClassGraphEmptyState(
    settings.language,
    classGraph,
    classGraphSearchQuery,
    classGraphDisplayMode
  );
  const isIndexRunDisabled =
    !selectedProjectId ||
    submittingIndex ||
    (indexMode === "incremental" && indexChangeSource === "manual" && manualChangedFiles.length === 0);
  const reviewBaseCommitValue = reviewBaseCommit.trim();
  const reviewTargetCommitValue = reviewTargetCommit.trim();
  const isReviewInputIncomplete =
    (reviewChangeSource === "manual" && reviewManualChangedFiles.length === 0) ||
    (reviewChangeSource === "commitRange" && (!reviewBaseCommitValue || !reviewTargetCommitValue));

  useEffect(() => {
    let isMounted = true;

    void loadUiSettings()
      .then((storedSettings) => {
        if (!isMounted) {
          return;
        }
        setSettings(storedSettings);
        setDraftSettings(storedSettings);
        setApiBaseUrl(storedSettings.apiBaseUrl);
      })
      .finally(() => {
        if (isMounted) {
          setSettingsLoaded(true);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    setApiBaseUrl(settings.apiBaseUrl);
  }, [settings.apiBaseUrl]);

  useEffect(() => {
    if (!settingsLoaded) {
      return;
    }
    void loadProjects();
  }, [settingsLoaded]);

  useEffect(() => {
    return subscribeToOpenSettings(() => {
      setDraftSettings(settings);
      setIsSettingsOpen(true);
    });
  }, [settings]);

  useEffect(() => {
    return subscribeToReloadWorkspace(() => {
      void handleReloadWorkspace();
    });
  }, [projects, selectedProject, selectedProjectId, selectedSnapshotId, settings.language]);

  useEffect(() => {
    const activeGroupKeys = new Set(snapshotGroups.map((group) => group.key));
    setCollapsedSnapshotGroupKeys((currentKeys) => {
      const nextKeys = currentKeys.filter((key) => activeGroupKeys.has(key));
      return nextKeys.length === currentKeys.length && nextKeys.every((key, index) => key === currentKeys[index])
        ? currentKeys
        : nextKeys;
    });
  }, [snapshotGroups]);

  useEffect(() => {
    if (!selectedSnapshotId) {
      return;
    }

    const selectedGroupKey = findSnapshotGroupKey(snapshots, selectedSnapshotId);
    if (!selectedGroupKey) {
      return;
    }

    setCollapsedSnapshotGroupKeys((currentKeys) =>
      currentKeys.includes(selectedGroupKey) ? currentKeys.filter((key) => key !== selectedGroupKey) : currentKeys
    );
  }, [selectedSnapshotId, snapshots]);

  useEffect(() => {
    if (!selectedSnapshotId) {
      setSnapshotCompareBaseId("");
      setSnapshotCompareResult(null);
      return;
    }

    const fallbackSnapshot = snapshots.find((snapshot) => snapshot.id !== selectedSnapshotId) ?? null;
    setSnapshotCompareBaseId((current) =>
      current && current !== selectedSnapshotId && snapshots.some((snapshot) => snapshot.id === current)
        ? current
        : (fallbackSnapshot?.id ?? "")
    );
    setSnapshotCompareResult(null);
  }, [selectedSnapshotId, snapshots]);

  useEffect(() => {
    if (!selectedClassId || classNodes.some((node) => node.id === selectedClassId)) {
      return;
    }

    setSelectedClassId(null);
    setSelectedMethodId(null);
    setMethodGraph(null);
  }, [classNodes, selectedClassId]);

  useEffect(() => {
    if (!contextMenu) {
      return;
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setContextMenu(null);
      }
    }

    function handleWindowChange() {
      setContextMenu(null);
    }

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener("blur", handleWindowChange);
    window.addEventListener("resize", handleWindowChange);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("blur", handleWindowChange);
      window.removeEventListener("resize", handleWindowChange);
    };
  }, [contextMenu]);

  useEffect(() => {
    if (!expandedGraphView) {
      return;
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setExpandedGraphView(null);
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [expandedGraphView]);

  useEffect(() => {
    if (expandedGraphView === "class") {
      setIsClassGraphOverlayCollapsed(false);
    }
  }, [expandedGraphView]);

  function syncImportFields(project: Project) {
    setImportName(project.name);
    setImportRootPath(project.rootPath);
  }

  async function loadSnapshotWorkspace(
    projectId: string,
    snapshotId: string,
    languageOverride?: LanguageMode,
    workspaceMessageOverride?: string
  ) {
    const activeCopy = getCopy(languageOverride ?? settings.language);
    setLoadingSnapshotDiagnostics(true);

    try {
      const [graph, changeList, diagnostics] = await Promise.all([
        getClassGraph(projectId, snapshotId),
        getChanges(projectId, snapshotId),
        getSnapshotDiagnostics(projectId, snapshotId)
      ]);

      setClassGraph(graph);
      setChanges(changeList);
      setSnapshotDiagnostics(diagnostics);
      setSelectedSnapshotId(graph.snapshotId);
      setWorkspaceMessage(
        workspaceMessageOverride ??
          activeCopy.messages.snapshotLoaded(shortId(graph.snapshotId), graph.nodes.length, graph.edges.length, changeList.length)
      );
    } finally {
      setLoadingSnapshotDiagnostics(false);
    }
  }

  async function loadProjects(preferredProjectId?: string, languageOverride?: LanguageMode) {
    const activeCopy = getCopy(languageOverride ?? settings.language);

    setLoadingProjects(true);
    setErrorMessage(null);
    try {
      const projectList = await listProjects();
      setProjects(projectList);

      const nextProjectId =
        preferredProjectId ??
        (selectedProjectId && projectList.some((project) => project.id === selectedProjectId)
          ? selectedProjectId
          : projectList[0]?.id ?? null);

      if (nextProjectId) {
        const nextProject = projectList.find((project) => project.id === nextProjectId) ?? null;
        if (nextProject) {
          syncImportFields(nextProject);
        }
        await openProject(nextProjectId, undefined, languageOverride, nextProject);
      } else {
        setSelectedProjectId(null);
        clearWorkspace();
        setWorkspaceMessage(null);
      }
    } catch (error) {
      setErrorMessage(toMessage(error, activeCopy.messages.unexpectedError));
    } finally {
      setLoadingProjects(false);
    }
  }

  async function openProject(
    projectId: string,
    preferredSnapshotId?: string | null,
    languageOverride?: LanguageMode,
    projectOverride?: Project | null
  ) {
    const activeCopy = getCopy(languageOverride ?? settings.language);
    const targetProject = projectOverride ?? projects.find((project) => project.id === projectId) ?? null;

    if (targetProject) {
      syncImportFields(targetProject);
    }

    setSelectedProjectId(projectId);
    setLoadingWorkspace(true);
    setErrorMessage(null);
    setWorkspaceMessage(null);
    setSelectedMethodId(null);
    setSelectedClassId(null);
    setMethodGraph(null);
    cancelSnapshotRename();
    closeContextMenu();

    try {
      const projectSnapshots = await listSnapshots(projectId);
      setSnapshots(projectSnapshots);

      const snapshotId =
        preferredSnapshotId && projectSnapshots.some((snapshot) => snapshot.id === preferredSnapshotId)
          ? preferredSnapshotId
          : projectSnapshots[0]?.id ?? null;

      if (!snapshotId) {
        setSubmittingIndex(true);
        const result = await triggerIndex(projectId, { mode: "full" });
        const refreshedSnapshots = await listSnapshots(projectId);
        setSnapshots(refreshedSnapshots);
        await loadSnapshotWorkspace(
          projectId,
          result.snapshot.id,
          languageOverride,
          activeCopy.messages.indexFinished(result.project.name, result.typeCount, result.methodCount, result.relationCount)
        );
        return;
      }

      setSelectedSnapshotId(snapshotId);
      await loadSnapshotWorkspace(projectId, snapshotId, languageOverride);
    } catch (error) {
      clearWorkspace();
      setSelectedProjectId(projectId);
      setErrorMessage(toMessage(error, activeCopy.messages.unexpectedError));
    } finally {
      setSubmittingIndex(false);
      setLoadingWorkspace(false);
    }
  }

  async function handleReloadWorkspace() {
    if (!selectedProjectId) {
      await loadProjects(undefined, settings.language);
      return;
    }

    await openProject(selectedProjectId, selectedSnapshotId, settings.language, selectedProject);
  }

  async function handleImportProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmittingImport(true);
    setErrorMessage(null);

    try {
      const project = await createProject({ name: importName.trim(), rootPath: importRootPath.trim() });
      setWorkspaceMessage(copy.messages.projectReady(project.name));
      await loadProjects(project.id);
    } catch (error) {
      setErrorMessage(
        toMessage(error, copy.messages.unexpectedError, {
          unsupported_project_language: copy.messages.unsupportedProjectLanguage
        })
      );
    } finally {
      setSubmittingImport(false);
    }
  }

  async function handleRunIndex(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProjectId) {
      setErrorMessage(copy.messages.selectProjectBeforeIndex);
      return;
    }

    setSubmittingIndex(true);
    setErrorMessage(null);
    try {
      const changedFiles = indexMode === "incremental" && indexChangeSource === "manual" ? manualChangedFiles : undefined;

      const result = await triggerIndex(selectedProjectId, {
        mode: indexMode,
        changeSource: indexMode === "incremental" ? indexChangeSource : undefined,
        changedFiles
      });

      setWorkspaceMessage(
        copy.messages.indexFinished(result.project.name, result.typeCount, result.methodCount, result.relationCount)
      );
      await openProject(selectedProjectId, result.snapshot.id);
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setSubmittingIndex(false);
    }
  }

  async function handleRunChangeSetReview() {
    if (!selectedProjectId) {
      setErrorMessage(copy.messages.selectProjectBeforeReview);
      return;
    }
    if (!selectedSnapshotId) {
      setErrorMessage(copy.messages.selectSnapshotBeforeReview);
      return;
    }

    setSubmittingReview(true);
    setErrorMessage(null);
    try {
      const result = await reviewChangeSet(selectedProjectId, buildChangeSetReviewPayload(selectedSnapshotId));
      setReviewResult(result);
      setReviewMarkdownReport(null);
      setWorkspaceMessage(copy.messages.reviewFinished(result.risk.level, result.reviewTargets.length));
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setSubmittingReview(false);
    }
  }

  async function handleExportChangeSetReviewMarkdown() {
    if (!selectedProjectId) {
      setErrorMessage(copy.messages.selectProjectBeforeReview);
      return;
    }
    if (!selectedSnapshotId) {
      setErrorMessage(copy.messages.selectSnapshotBeforeReview);
      return;
    }

    setExportingReviewMarkdown(true);
    setErrorMessage(null);
    try {
      const report = await exportChangeSetReviewMarkdown(selectedProjectId, buildChangeSetReviewPayload(selectedSnapshotId));
      setReviewMarkdownReport(report);
      downloadMarkdownReport(report);
      setWorkspaceMessage(copy.messages.markdownExported(report.fileName));
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setExportingReviewMarkdown(false);
    }
  }

  function buildChangeSetReviewPayload(snapshotId: string): ChangeSetReviewPayload {
    if (reviewChangeSource === "manual") {
      return {
        snapshotId,
        changeSource: "manual",
        changedFiles: reviewManualChangedFiles
      };
    }

    if (reviewChangeSource === "commitRange") {
      return {
        snapshotId,
        changeSource: "git",
        baseCommit: reviewBaseCommitValue,
        targetCommit: reviewTargetCommitValue
      };
    }

    return {
      snapshotId,
      changeSource: "git"
    };
  }

  async function handleRunSnapshotCompare() {
    if (!selectedProjectId || !selectedSnapshotId || !snapshotCompareBaseId) {
      return;
    }

    setLoadingSnapshotCompare(true);
    setErrorMessage(null);
    try {
      const result = await compareSnapshots(selectedProjectId, snapshotCompareBaseId, selectedSnapshotId);
      setSnapshotCompareResult(result);
      setWorkspaceMessage(result.note);
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setLoadingSnapshotCompare(false);
    }
  }

  async function handleDeleteProject(projectOverride?: Project) {
    const projectToDelete = projectOverride ?? selectedProject;
    if (!projectToDelete) {
      return;
    }
    if (!globalThis.confirm(copy.messages.confirmDeleteProject(projectToDelete.name))) {
      return;
    }

    closeContextMenu();

    const deletedProjectId = projectToDelete.id;
    const deletedProjectName = projectToDelete.name;
    const fallbackProjectId =
      deletedProjectId === selectedProjectId
        ? projects.find((project) => project.id !== deletedProjectId)?.id
        : selectedProjectId;

    setDeletingProject(true);
    setErrorMessage(null);
    try {
      await deleteProject(deletedProjectId);
      await loadProjects(fallbackProjectId ?? undefined);
      if (!fallbackProjectId) {
        setSelectedProjectId(null);
      }
      setWorkspaceMessage(copy.messages.projectDeleted(deletedProjectName));
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setDeletingProject(false);
    }
  }

  function handleStartSnapshotRename(snapshot: ProjectSnapshot) {
    closeContextMenu();
    setRenamingSnapshotId(snapshot.id);
    setSnapshotNameDraft(snapshot.displayName);
    setErrorMessage(null);
  }

  function cancelSnapshotRename() {
    setRenamingSnapshotId(null);
    setSnapshotNameDraft("");
  }

  async function handleRenameSnapshot(snapshot: ProjectSnapshot) {
    if (!selectedProjectId) {
      return;
    }

    const normalizedDisplayName = snapshotNameDraft.trim();
    if (!normalizedDisplayName) {
      setErrorMessage(snapshotUiCopy.nameRequired);
      return;
    }

    setErrorMessage(null);
    try {
      const updatedSnapshot = await renameSnapshot(selectedProjectId, snapshot.id, {
        displayName: normalizedDisplayName
      });
      setSnapshots((currentSnapshots) =>
        currentSnapshots.map((item) => (item.id === updatedSnapshot.id ? updatedSnapshot : item))
      );
      cancelSnapshotRename();
      setWorkspaceMessage(snapshotUiCopy.renamed(updatedSnapshot.displayName));
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    }
  }

  async function handleDeleteSnapshot(snapshot: ProjectSnapshot) {
    if (!selectedProjectId) {
      return;
    }
    if (!globalThis.confirm(snapshotUiCopy.confirmDelete(resolveSnapshotDisplayName(snapshot)))) {
      return;
    }

    closeContextMenu();

    const remainingSnapshots = snapshots.filter((item) => item.id !== snapshot.id);
    const fallbackSnapshotId = snapshot.id === selectedSnapshotId ? remainingSnapshots[0]?.id ?? null : selectedSnapshotId;

    setDeletingSnapshotId(snapshot.id);
    setErrorMessage(null);
    try {
      await deleteSnapshot(selectedProjectId, snapshot.id);
      if (renamingSnapshotId === snapshot.id) {
        cancelSnapshotRename();
      }

      if (snapshot.id === selectedSnapshotId) {
        if (fallbackSnapshotId) {
          await openProject(selectedProjectId, fallbackSnapshotId, settings.language, selectedProject);
        } else {
          clearWorkspace();
          setSelectedProjectId(selectedProjectId);
          setWorkspaceMessage(snapshotUiCopy.deleted(resolveSnapshotDisplayName(snapshot)));
        }
      } else {
        setSnapshots(remainingSnapshots);
        setWorkspaceMessage(snapshotUiCopy.deleted(resolveSnapshotDisplayName(snapshot)));
      }
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setDeletingSnapshotId(null);
    }
  }

  async function handleSelectClass(node: GraphNode) {
    if (!selectedProjectId || !selectedSnapshotId) {
      return;
    }

    setSelectedClassId(node.id);
    setSelectedMethodId(null);
    setErrorMessage(null);

    try {
      const nextMethodGraph = await getMethodGraph(selectedProjectId, node.id, selectedSnapshotId);
      setMethodGraph(nextMethodGraph);
      setWorkspaceMessage(copy.messages.classExpanded(node.name, nextMethodGraph.nodes.length));
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    }
  }

  function handleOpenSettings() {
    closeContextMenu();
    setDraftSettings(settings);
    setIsSettingsOpen(true);
  }

  function renderGraphExpandAction(view: "class" | "method") {
    return (
      <button
        type="button"
        className="graph-corner-button"
        aria-label={copy.buttons.expand}
        title={copy.buttons.expand}
        onClick={() => setExpandedGraphView(view)}
      />
    );
  }

  function renderClassGraphFilterControls(isFloating = false) {
    const isCollapsed = isFloating && isClassGraphOverlayCollapsed;
    const overlayToggleLabel = getClassGraphOverlayToggleLabel(settings.language, isCollapsed);

    return (
      <div className={`class-graph-controls ${isFloating ? "is-floating" : ""} ${isCollapsed ? "is-collapsed" : ""}`}>
        {isFloating ? (
          <div className="class-graph-controls-toggle-row">
            <button
              type="button"
              className={`class-graph-controls-toggle ${isCollapsed ? "is-collapsed" : ""}`}
              aria-label={overlayToggleLabel}
              title={overlayToggleLabel}
              onClick={() => setIsClassGraphOverlayCollapsed((current) => !current)}
            />
          </div>
        ) : null}

        {isCollapsed ? null : (
          <div className={`class-graph-controls-body ${isFloating ? "is-floating" : ""}`}>
            <label className="field class-graph-search">
              <span>{classGraphSearchLabel}</span>
              <input
                value={classGraphSearchQuery}
                onChange={(event) => setClassGraphSearchQuery(event.target.value)}
                placeholder={classGraphSearchPlaceholder}
                aria-label={classGraphSearchLabel}
              />
            </label>
            <div className="class-graph-visibility">
              <span className="class-graph-visibility-label">{classGraphDisplayLabel}</span>
              <div className="segmented-control" role="tablist" aria-label={classGraphDisplayLabel}>
                <button
                  type="button"
                  className={classGraphDisplayMode === "full" ? "is-active" : ""}
                  onClick={() => setClassGraphDisplayMode("full")}
                >
                  {copy.buttons.full}
                </button>
                <button
                  type="button"
                  className={classGraphDisplayMode === "incremental" ? "is-active" : ""}
                  onClick={() => setClassGraphDisplayMode("incremental")}
                >
                  {copy.buttons.incremental}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  function renderClassGraphPanelBody(isExpanded = false) {
    return (
      <div className={`graph-panel-body ${isExpanded ? "is-expanded" : ""}`}>
        {isExpanded ? null : renderClassGraphFilterControls()}
        <Suspense fallback={<GraphLoadingState title={classGraphEmptyState.title} body={copy.copy.graphLoadingBody} />}>
          <GraphCanvas
            nodes={filteredClassGraph?.nodes ?? []}
            edges={filteredClassGraph?.edges ?? []}
            immersive={isExpanded}
            selectedNodeId={selectedClassId}
            sceneStorageKey={classGraphSceneStorageKey}
            onNodeClick={(node) => void handleSelectClass(node)}
            emptyTitle={classGraphEmptyState.title}
            emptyBody={classGraphEmptyState.body}
            formatEdgeType={(edgeType) => formatClassGraphEdgeType(edgeType, settings.language)}
            formatNodeKind={(kind) => formatKindLabel(kind, "en")}
            labels={copy.graph}
            overlayAction={isExpanded ? null : renderGraphExpandAction("class")}
            overlayPanel={isExpanded ? renderClassGraphFilterControls(true) : null}
          />
        </Suspense>
      </div>
    );
  }

  function renderMethodGraphPanelBody(isExpanded = false) {
    return (
      <div className={`graph-panel-body ${isExpanded ? "is-expanded" : ""}`}>
        <Suspense fallback={<GraphLoadingState title={copy.copy.methodGraphEmptyTitle} body={copy.copy.graphLoadingBody} />}>
          <GraphCanvas
            nodes={methodGraph?.nodes ?? []}
            edges={methodGraph?.edges ?? []}
            immersive={isExpanded}
            selectedNodeId={selectedMethodId}
            sceneStorageKey={methodGraphSceneStorageKey}
            onNodeClick={(node) => setSelectedMethodId(node.id)}
            emptyTitle={copy.copy.methodGraphEmptyTitle}
            emptyBody={copy.copy.methodGraphEmptyBody}
            formatEdgeType={(edgeType) => formatEdgeTypeLabel(edgeType, settings.language)}
            formatNodeKind={(kind) => formatKindLabel(kind, "en")}
            labels={copy.graph}
            overlayAction={isExpanded ? null : renderGraphExpandAction("method")}
          />
        </Suspense>
      </div>
    );
  }

  function handleCancelSettings() {
    setDraftSettings(settings);
    setIsSettingsOpen(false);
  }

  async function handleSaveSettings() {
    try {
      const nextSettings = await saveUiSettings(draftSettings, settings);
      const nextCopy = getCopy(nextSettings.language);
      const apiBaseUrlChanged = nextSettings.apiBaseUrl !== settings.apiBaseUrl;

      setSettings(nextSettings);
      setDraftSettings(nextSettings);
      setIsSettingsOpen(false);
      setErrorMessage(null);
      setApiBaseUrl(nextSettings.apiBaseUrl);

      if (apiBaseUrlChanged) {
        await loadProjects(undefined, nextSettings.language);
        return;
      }

      setWorkspaceMessage(nextCopy.messages.settingsSaved);
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    }
  }

  function clearWorkspace() {
    setSnapshots([]);
    setSelectedSnapshotId(null);
    setSnapshotDiagnostics(null);
    setLoadingSnapshotDiagnostics(false);
    setCollapsedSnapshotGroupKeys([]);
    setClassGraph(null);
    setMethodGraph(null);
    setChanges([]);
    setReviewResult(null);
    setReviewMarkdownReport(null);
    setSnapshotCompareResult(null);
    setSelectedClassId(null);
    setSelectedMethodId(null);
    setWorkspaceMessage(null);
    cancelSnapshotRename();
    closeContextMenu();
  }

  function handleToggleSnapshotGroup(groupKey: string) {
    setCollapsedSnapshotGroupKeys((currentKeys) =>
      currentKeys.includes(groupKey) ? currentKeys.filter((key) => key !== groupKey) : [...currentKeys, groupKey]
    );
  }

  function closeContextMenu() {
    setContextMenu(null);
  }

  function handleProjectContextMenu(event: ReactMouseEvent<HTMLElement>, project: Project) {
    event.preventDefault();
    setContextMenu({
      kind: "project",
      project,
      ...resolveContextMenuPosition(event.clientX, event.clientY)
    });
  }

  function handleSnapshotContextMenu(event: ReactMouseEvent<HTMLElement>, snapshot: ProjectSnapshot) {
    event.preventDefault();
    setContextMenu({
      kind: "snapshot",
      snapshot,
      ...resolveContextMenuPosition(event.clientX, event.clientY)
    });
  }

  function buildContextMenuItems(menuState: ContextMenuState): ContextMenuItem[] {
    if (menuState.kind === "project") {
      return [
        {
          danger: true,
          disabled: deletingProject,
          label: deletingProject ? copy.states.deletingProject : contextMenuCopy.deleteProject,
          onSelect: () => void handleDeleteProject(menuState.project)
        }
      ];
    }

    return [
      {
        disabled: deletingSnapshotId === menuState.snapshot.id,
        label: contextMenuCopy.renameSnapshot,
        onSelect: () => handleStartSnapshotRename(menuState.snapshot)
      },
      {
        danger: true,
        disabled: deletingSnapshotId === menuState.snapshot.id,
        label: deletingSnapshotId === menuState.snapshot.id ? snapshotUiCopy.deleting : contextMenuCopy.deleteSnapshot,
        onSelect: () => void handleDeleteSnapshot(menuState.snapshot)
      }
    ];
  }

  return (
    <>
      <main className="app-shell">
        <div className="app-frame">
          <aside className="sidebar">
            <div className="brand">
              <div className="brand-mark">JG</div>
              <div className="brand-copy">
                <h1>{copy.heroEyebrow}</h1>
                <p>{copy.heroSummary}</p>
              </div>
            </div>

            <section className="sidebar-card">
              <div className="sidebar-label">{copy.panels.importProjectTitle}</div>
              <p className="sidebar-copy">{copy.panels.importProjectSubtitle}</p>
              <form className="stack-form" onSubmit={handleImportProject}>
                <label className="field">
                  <span>{copy.fields.name}</span>
                  <input
                    value={importName}
                    onChange={(event) => setImportName(event.target.value)}
                    placeholder={copy.placeholders.name}
                  />
                </label>
                <label className="field">
                  <span>{copy.fields.rootPath}</span>
                  <input
                    value={importRootPath}
                    onChange={(event) => setImportRootPath(event.target.value)}
                    placeholder={copy.placeholders.rootPath}
                  />
                </label>
                <button className="primary-button" type="submit" disabled={submittingImport}>
                  {submittingImport ? copy.states.importing : copy.buttons.importProject}
                </button>
              </form>
            </section>

            <section className="sidebar-card">
              <div className="sidebar-label">{copy.panels.projectsTitle}</div>
              <p className="sidebar-copy">{copy.subtitles.projects(loadingProjects)}</p>
              <div className="list-stack">
                {projects.length === 0 ? (
                  <EmptyState title={copy.copy.projectsEmptyTitle} body={copy.copy.projectsEmptyBody} />
                ) : (
                  projects.map((project) => (
                    <button
                      key={project.id}
                      type="button"
                      className={`project-tile ${project.id === selectedProjectId ? "is-active" : ""}`}
                      onClick={() => void openProject(project.id, undefined, undefined, project)}
                      onContextMenu={(event) => handleProjectContextMenu(event, project)}
                    >
                      <strong>{project.name}</strong>
                      <span>{project.buildTool}</span>
                      <code>{project.rootPath}</code>
                    </button>
                  ))
                )}
              </div>
            </section>

            <section className="sidebar-card">
              <div className="sidebar-label">{copy.panels.indexControlTitle}</div>
              <p className="sidebar-copy">{copy.panels.indexControlSubtitle}</p>
              <form className="stack-form" onSubmit={handleRunIndex}>
                <div className="segmented-control" role="tablist" aria-label={copy.panels.indexControlTitle}>
                  <button type="button" className={indexMode === "full" ? "is-active" : ""} onClick={() => setIndexMode("full")}>
                    {copy.buttons.full}
                  </button>
                  <button
                    type="button"
                    className={indexMode === "incremental" ? "is-active" : ""}
                    onClick={() => setIndexMode("incremental")}
                  >
                    {copy.buttons.incremental}
                  </button>
                </div>

                {indexMode === "incremental" ? (
                  <>
                    <label className="field">
                      <span>{copy.fields.incrementalSource}</span>
                      <div className="segmented-control" role="tablist" aria-label={copy.fields.incrementalSource}>
                        <button
                          type="button"
                          className={indexChangeSource === "git" ? "is-active" : ""}
                          onClick={() => setIndexChangeSource("git")}
                        >
                          {copy.buttons.gitAuto}
                        </button>
                        <button
                          type="button"
                          className={indexChangeSource === "manual" ? "is-active" : ""}
                          onClick={() => setIndexChangeSource("manual")}
                        >
                          {copy.buttons.manual}
                        </button>
                      </div>
                    </label>

                    {indexChangeSource === "manual" ? (
                      <>
                        <label className="field">
                          <span>{copy.fields.changedFiles}</span>
                          <textarea
                            value={changedFilesText}
                            onChange={(event) => setChangedFilesText(event.target.value)}
                            placeholder={copy.placeholders.changedFiles}
                            rows={5}
                          />
                        </label>
                        <p className="helper-copy">{copy.copy.incrementalManualHint}</p>
                      </>
                    ) : (
                      <p className="helper-copy">{copy.copy.incrementalGitHint}</p>
                    )}
                  </>
                ) : (
                  <p className="helper-copy">{copy.copy.fullIndexHint}</p>
                )}

                <button className="primary-button" type="submit" disabled={isIndexRunDisabled}>
                  {submittingIndex ? copy.states.indexing : copy.buttons.runIndex}
                </button>
              </form>
            </section>

            <section className="sidebar-card">
              <div className="sidebar-label">{copy.panels.snapshotsTitle}</div>
              <p className="sidebar-copy">{copy.panels.snapshotsSubtitle}</p>
              <SnapshotHistoryList
                collapsedGroupKeys={collapsedSnapshotGroupKeys}
                emptyBody={copy.copy.snapshotsEmptyBody}
                emptyTitle={copy.copy.snapshotsEmptyTitle}
                language={settings.language}
                locale={copy.locale}
                onCollapseGroupToggle={handleToggleSnapshotGroup}
                onOpenSnapshot={(snapshot) => void openProject(snapshot.projectId, snapshot.id)}
                onRenameCancel={cancelSnapshotRename}
                onRenameDraftChange={setSnapshotNameDraft}
                onRenameSnapshot={(snapshot) => void handleRenameSnapshot(snapshot)}
                onSnapshotContextMenu={handleSnapshotContextMenu}
                renamingSnapshotId={renamingSnapshotId}
                selectedSnapshotId={selectedSnapshotId}
                snapshotGroups={snapshotGroups}
                snapshotNameDraft={snapshotNameDraft}
                snapshotUiCopy={snapshotUiCopy}
              />
            </section>
          </aside>

          <section className="content">
            <header className="toolbar">
              <div className="toolbar-head">
                <div className="toolbar-title">
                  <p className="eyebrow">{copy.heroEyebrow}</p>
                  <h2>{selectedProject?.name ?? copy.heroEyebrow}</h2>
                  <p>{selectedProject ? selectedProject.rootPath : copy.heroTitle}</p>
                </div>
                <div className="toolbar-actions">
                  <MetricBadge label={copy.metrics.projects} value={String(projects.length)} />
                  <MetricBadge label={copy.metrics.snapshots} value={String(snapshots.length)} />
                  <MetricBadge label={copy.metrics.changes} value={String(changes.length)} />
                  <button type="button" className="secondary-button" onClick={handleOpenSettings}>
                    {copy.settingsButton}
                  </button>
                </div>
              </div>
            </header>

            {errorMessage ? <div className="banner banner-error">{errorMessage}</div> : null}
            {workspaceMessage ? <div className="banner banner-info">{workspaceMessage}</div> : null}

            <section className="workspace-layout">
              {expandedGraphView ? null : (
                <section className="graph-column">
                  <Panel title={copy.panels.classGraphTitle} subtitle={copy.subtitles.classGraph(selectedProject?.name ?? null)}>
                    {renderClassGraphPanelBody(false)}
                  </Panel>

                  <Panel title={copy.panels.methodGraphTitle} subtitle={copy.subtitles.methodGraph(selectedClass?.name ?? null)}>
                    {renderMethodGraphPanelBody(false)}
                  </Panel>
                </section>
              )}

              <aside className="inspector-column">
                <Panel title={copy.panels.selectionTitle} subtitle={copy.panels.selectionSubtitle}>
                  {selectedMethod ? (
                    <SelectionCard
                      title={selectedMethod.name}
                      subtitle={selectedMethod.qualifiedName}
                      status={selectedMethod.status}
                      statusLabel={formatStatusLabel(selectedMethod.status, settings.language)}
                      kind={formatKindLabel(selectedMethod.kind, settings.language)}
                      detail={selectedChange?.reason ?? copy.copy.noMethodChange}
                    />
                  ) : selectedClass ? (
                    <SelectionCard
                      title={selectedClass.name}
                      subtitle={selectedClass.qualifiedName}
                      status={selectedClass.status}
                      statusLabel={formatStatusLabel(selectedClass.status, settings.language)}
                      kind={formatKindLabel(selectedClass.kind, settings.language)}
                      detail={selectedChange?.reason ?? copy.copy.noTypeChange}
                    />
                  ) : (
                    <EmptyState title={copy.copy.noWorkspaceSelected} body={copy.copy.noWorkspaceSelectedBody} />
                  )}
                </Panel>

                <Panel title={copy.panels.changesTitle} subtitle={copy.panels.changesSubtitle}>
                  <ChangeList
                    changes={changes}
                    emptyTitle={copy.copy.changesEmptyTitle}
                    emptyBody={copy.copy.changesEmptyBody}
                    language={settings.language}
                  />
                </Panel>

                <Panel title={snapshotDiagnosticsCopy.title} subtitle={snapshotDiagnosticsCopy.subtitle}>
                  <SnapshotDiagnosticsPanel
                    diagnostics={snapshotDiagnostics}
                    emptyBody={snapshotDiagnosticsCopy.emptyBody}
                    emptyTitle={snapshotDiagnosticsCopy.emptyTitle}
                    labels={snapshotDiagnosticsCopy}
                    language={settings.language}
                    loading={loadingSnapshotDiagnostics}
                    selectedSnapshot={selectedSnapshot}
                  />
                </Panel>

                <Panel
                  title={settings.language === "zh" ? "快照对比" : "Snapshot Compare"}
                  subtitle={
                    settings.language === "zh"
                      ? "选择一个基线快照，对比当前快照的符号级差异。"
                      : "Choose a base snapshot and compare symbol-level diffs against the current snapshot."
                  }
                >
                  <SnapshotComparePanel
                    baseSnapshotId={snapshotCompareBaseId}
                    compareLabel={settings.language === "zh" ? "基线快照" : "Base Snapshot"}
                    compareResult={snapshotCompareResult}
                    emptyBody={
                      settings.language === "zh"
                        ? "至少保留两个快照后，才能对比历史与当前结果。"
                        : "Keep at least two snapshots to compare history against the current result."
                    }
                    emptyTitle={settings.language === "zh" ? "暂无可对比快照" : "No comparable snapshots"}
                    language={settings.language}
                    loading={loadingSnapshotCompare}
                    onBaseSnapshotChange={setSnapshotCompareBaseId}
                    onRunCompare={() => void handleRunSnapshotCompare()}
                    runLabel={settings.language === "zh" ? "运行对比" : "Run Compare"}
                    snapshots={snapshots}
                    targetSnapshot={selectedSnapshot}
                  />
                </Panel>

                <Panel
                  title={copy.panels.reviewExportTitle}
                  subtitle={copy.panels.reviewExportSubtitle}
                  actions={
                    <div className="panel-header-actions">
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => void handleRunChangeSetReview()}
                        disabled={submittingReview || !selectedProjectId || !selectedSnapshotId || isReviewInputIncomplete}
                      >
                        {submittingReview ? copy.states.reviewing : copy.buttons.runReview}
                      </button>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => void handleExportChangeSetReviewMarkdown()}
                        disabled={exportingReviewMarkdown || !selectedProjectId || !selectedSnapshotId || isReviewInputIncomplete}
                      >
                        {exportingReviewMarkdown ? copy.states.exportingMarkdown : copy.buttons.exportMarkdown}
                      </button>
                    </div>
                  }
                >
                  <div className="stack-form">
                    <label className="field">
                      <span>{copy.fields.reviewSource}</span>
                      <div className="segmented-control" role="tablist" aria-label={copy.fields.reviewSource}>
                        <button
                          type="button"
                          className={reviewChangeSource === "git" ? "is-active" : ""}
                          onClick={() => setReviewChangeSource("git")}
                        >
                          {copy.buttons.gitAuto}
                        </button>
                        <button
                          type="button"
                          className={reviewChangeSource === "manual" ? "is-active" : ""}
                          onClick={() => setReviewChangeSource("manual")}
                        >
                          {copy.buttons.manual}
                        </button>
                        <button
                          type="button"
                          className={reviewChangeSource === "commitRange" ? "is-active" : ""}
                          onClick={() => setReviewChangeSource("commitRange")}
                        >
                          {copy.buttons.commitRange}
                        </button>
                      </div>
                    </label>

                    {reviewChangeSource === "manual" ? (
                      <>
                        <label className="field">
                          <span>{copy.fields.changedFiles}</span>
                          <textarea
                            value={reviewChangedFilesText}
                            onChange={(event) => setReviewChangedFilesText(event.target.value)}
                            placeholder={copy.placeholders.changedFiles}
                            rows={5}
                          />
                        </label>
                        <p className="helper-copy">{copy.copy.reviewManualHint}</p>
                      </>
                    ) : null}

                    {reviewChangeSource === "commitRange" ? (
                      <>
                        <label className="field">
                          <span>{copy.fields.baseCommit}</span>
                          <input
                            value={reviewBaseCommit}
                            onChange={(event) => setReviewBaseCommit(event.target.value)}
                            placeholder={copy.placeholders.baseCommit}
                          />
                        </label>
                        <label className="field">
                          <span>{copy.fields.targetCommit}</span>
                          <input
                            value={reviewTargetCommit}
                            onChange={(event) => setReviewTargetCommit(event.target.value)}
                            placeholder={copy.placeholders.targetCommit}
                          />
                        </label>
                        <p className="helper-copy">{copy.copy.reviewCommitRangeHint}</p>
                      </>
                    ) : null}

                    {reviewChangeSource === "git" ? <p className="helper-copy">{copy.copy.reviewGitHint}</p> : null}
                  </div>

                  <ChangeSetReviewPanel
                    changedFiles={reviewChangeSource === "manual" ? reviewManualChangedFiles : null}
                    emptyBody={copy.copy.reviewExportEmptyBody}
                    emptyTitle={copy.copy.reviewExportEmptyTitle}
                    language={settings.language}
                    markdownLabel={copy.copy.reviewMarkdownLabel}
                    report={reviewMarkdownReport}
                    result={reviewResult}
                    reviewTargetsLabel={copy.copy.reviewTargetsLabel}
                    reviewSourceLabel={copy.fields.reviewSource}
                    reviewSourceValue={reviewChangeSource}
                  />
                </Panel>

                <Panel title={copy.panels.reviewNotesTitle} subtitle={copy.panels.reviewNotesSubtitle}>
                  <ul className="review-note-list">
                    {copy.copy.reviewNotes.map((note) => (
                      <li key={note}>{note}</li>
                    ))}
                  </ul>
                </Panel>
              </aside>
            </section>
          </section>
        </div>
      </main>

      {contextMenu ? (
        <ContextMenu items={buildContextMenuItems(contextMenu)} x={contextMenu.x} y={contextMenu.y} onClose={closeContextMenu} />
      ) : null}

      {expandedGraphView ? (
        <div className="graph-expanded-overlay">
          <div className="graph-expanded-shell">
            <header className="graph-expanded-header">
              <div className="graph-expanded-copy">
                <h2>{expandedGraphView === "class" ? copy.panels.classGraphTitle : copy.panels.methodGraphTitle}</h2>
                <p>
                  {expandedGraphView === "class"
                    ? copy.subtitles.classGraph(selectedProject?.name ?? null)
                    : copy.subtitles.methodGraph(selectedClass?.name ?? null)}
                </p>
              </div>
              <div className="graph-expanded-actions">
                <button type="button" className="secondary-button" onClick={() => setExpandedGraphView(null)}>
                  {copy.buttons.back}
                </button>
              </div>
            </header>
            <div className="graph-expanded-body">
              <div className="graph-expanded-content">
                {expandedGraphView === "class" ? renderClassGraphPanelBody(true) : renderMethodGraphPanelBody(true)}
              </div>
            </div>
          </div>
        </div>
      ) : null}

      <SettingsDrawer
        copy={copy}
        draftSettings={draftSettings}
        isOpen={isSettingsOpen}
        onApiBaseUrlChange={(value) => setDraftSettings((currentSettings) => ({ ...currentSettings, apiBaseUrl: value }))}
        onCancel={handleCancelSettings}
        onClose={handleCancelSettings}
        onLanguageChange={(language) => setDraftSettings((currentSettings) => ({ ...currentSettings, language }))}
        onSave={() => void handleSaveSettings()}
        onUseDefaultApiBaseUrl={() =>
          setDraftSettings((currentSettings) => ({
            ...currentSettings,
            apiBaseUrl: runtime.mode === "desktop" ? runtime.defaultApiBaseUrl : ""
          }))
        }
        runtime={runtime}
      />
    </>
  );
}

export default App;
