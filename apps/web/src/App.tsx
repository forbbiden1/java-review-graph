import { type FormEvent, type MouseEvent as ReactMouseEvent, type ReactNode, useEffect, useState } from "react";
import {
  exportChangeSetReviewMarkdown,
  type ClassGraph,
  type ChangeSetReviewMarkdownReport,
  type ChangeSetReviewResult,
  type ChangeSetReviewSymbol,
  createProject,
  deleteProject,
  deleteSnapshot,
  getChanges,
  getClassGraph,
  getSnapshotDiagnostics,
  getMethodGraph,
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
import { GraphCanvas } from "./graph/GraphCanvas";
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
type ClassGraphDisplayMode = "full" | "incremental";
type IndexChangeSource = "git" | "manual";
type ExpandedGraphView = "class" | "method" | null;
type ContextMenuState =
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
  const [classGraphDisplayMode, setClassGraphDisplayMode] = useState<ClassGraphDisplayMode>("full");
  const [classGraphSearchQuery, setClassGraphSearchQuery] = useState("");
  const [changedFilesText, setChangedFilesText] = useState("");
  const [reviewResult, setReviewResult] = useState<ChangeSetReviewResult | null>(null);
  const [reviewMarkdownReport, setReviewMarkdownReport] = useState<ChangeSetReviewMarkdownReport | null>(null);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingWorkspace, setLoadingWorkspace] = useState(false);
  const [loadingSnapshotDiagnostics, setLoadingSnapshotDiagnostics] = useState(false);
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
      const result = await reviewChangeSet(selectedProjectId, {
        snapshotId: selectedSnapshotId,
        changeSource: indexChangeSource,
        changedFiles: indexChangeSource === "manual" ? manualChangedFiles : undefined
      });
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
      const report = await exportChangeSetReviewMarkdown(selectedProjectId, {
        snapshotId: selectedSnapshotId,
        changeSource: indexChangeSource,
        changedFiles: indexChangeSource === "manual" ? manualChangedFiles : undefined
      });
      setReviewMarkdownReport(report);
      downloadMarkdownReport(report);
      setWorkspaceMessage(copy.messages.markdownExported(report.fileName));
    } catch (error) {
      setErrorMessage(toMessage(error, copy.messages.unexpectedError));
    } finally {
      setExportingReviewMarkdown(false);
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
      </div>
    );
  }

  function renderMethodGraphPanelBody(isExpanded = false) {
    return (
      <div className={`graph-panel-body ${isExpanded ? "is-expanded" : ""}`}>
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
                  <button
                    type="button"
                    className={indexMode === "full" ? "is-active" : ""}
                    onClick={() => setIndexMode("full")}
                  >
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
              <div className="list-stack">
                {snapshots.length === 0 ? (
                  <EmptyState title={copy.copy.snapshotsEmptyTitle} body={copy.copy.snapshotsEmptyBody} />
                ) : (
                  snapshotGroups.map((group) => (
                    <section key={group.key} className="snapshot-group">
                      <button
                        type="button"
                        className={`snapshot-group-head ${collapsedSnapshotGroupKeys.includes(group.key) ? "is-collapsed" : ""}`}
                        onClick={() => handleToggleSnapshotGroup(group.key)}
                        aria-expanded={!collapsedSnapshotGroupKeys.includes(group.key)}
                      >
                        <span className="snapshot-group-label">
                          <span className="snapshot-group-chevron" aria-hidden="true">
                            {collapsedSnapshotGroupKeys.includes(group.key) ? ">" : "v"}
                          </span>
                          <strong title={group.title}>{group.title}</strong>
                        </span>
                        <span className="snapshot-group-meta">
                          {group.shortCommit ? <code>{group.shortCommit}</code> : null}
                          <span className="snapshot-group-count">{group.snapshots.length}</span>
                        </span>
                      </button>
                      {!collapsedSnapshotGroupKeys.includes(group.key) ? (
                        <div className="list-stack">
                          {group.snapshots.map((snapshot) => (
                            <article
                              key={snapshot.id}
                              className={`snapshot-row ${snapshot.id === selectedSnapshotId ? "is-active" : ""}`}
                              onContextMenu={(event) => handleSnapshotContextMenu(event, snapshot)}
                            >
                              {renamingSnapshotId === snapshot.id ? (
                                <form
                                  className="snapshot-rename-form"
                                  onSubmit={(event) => {
                                    event.preventDefault();
                                    void handleRenameSnapshot(snapshot);
                                  }}
                                >
                                  <input
                                    className="snapshot-rename-input"
                                    value={snapshotNameDraft}
                                    onChange={(event) => setSnapshotNameDraft(event.target.value)}
                                    placeholder={snapshotUiCopy.namePlaceholder}
                                    maxLength={200}
                                  />
                                  <div className="snapshot-rename-actions">
                                    <button type="submit" className="secondary-button">
                                      {snapshotUiCopy.save}
                                    </button>
                                    <button type="button" className="ghost-button" onClick={cancelSnapshotRename}>
                                      {snapshotUiCopy.cancel}
                                    </button>
                                  </div>
                                </form>
                              ) : (
                                <div className="snapshot-row-body">
                                  <button
                                    type="button"
                                    className="snapshot-select-button"
                                    onClick={() => void openProject(snapshot.projectId, snapshot.id)}
                                  >
                                    <strong>{resolveSnapshotDisplayName(snapshot)}</strong>
                                    <span>{new Date(snapshot.createdAt).toLocaleString(copy.locale)}</span>
                                    {snapshot.displayName !== snapshot.id ? <code>{shortId(snapshot.id)}</code> : null}
                                  </button>
                                </div>
                              )}
                            </article>
                          ))}
                        </div>
                      ) : null}
                    </section>
                  ))
                )}
              </div>
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
                  title={copy.panels.reviewExportTitle}
                  subtitle={copy.panels.reviewExportSubtitle}
                  actions={
                    <div className="panel-header-actions">
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => void handleRunChangeSetReview()}
                        disabled={submittingReview || !selectedProjectId || !selectedSnapshotId}
                      >
                        {submittingReview ? copy.states.reviewing : copy.buttons.runReview}
                      </button>
                      <button
                        type="button"
                        className="secondary-button"
                        onClick={() => void handleExportChangeSetReviewMarkdown()}
                        disabled={exportingReviewMarkdown || !selectedProjectId || !selectedSnapshotId}
                      >
                        {exportingReviewMarkdown ? copy.states.exportingMarkdown : copy.buttons.exportMarkdown}
                      </button>
                    </div>
                  }
                >
                  <ChangeSetReviewPanel
                    changedFiles={indexChangeSource === "manual" ? manualChangedFiles : null}
                    emptyBody={copy.copy.reviewExportEmptyBody}
                    emptyTitle={copy.copy.reviewExportEmptyTitle}
                    language={settings.language}
                    markdownLabel={copy.copy.reviewMarkdownLabel}
                    report={reviewMarkdownReport}
                    result={reviewResult}
                    reviewTargetsLabel={copy.copy.reviewTargetsLabel}
                    reviewSourceLabel={copy.fields.reviewSource}
                    reviewSourceValue={indexChangeSource}
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
        <ContextMenu
          items={buildContextMenuItems(contextMenu)}
          x={contextMenu.x}
          y={contextMenu.y}
          onClose={closeContextMenu}
        />
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

type PanelProps = {
  actions?: ReactNode;
  children: ReactNode;
  subtitle: string;
  title: string;
};

function Panel({ actions, title, subtitle, children }: PanelProps) {
  return (
    <section className="panel">
      <header className="panel-header">
        <div className="panel-header-copy">
          <h2>{title}</h2>
          <p>{subtitle}</p>
        </div>
        {actions ? <div className="panel-header-actions">{actions}</div> : null}
      </header>
      {children}
    </section>
  );
}

function MetricBadge({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-badge">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

const CHANGE_PAGE_SIZE = 6;
const PREFERRED_CHANGE_FILTER_ORDER = ["added", "modified_api", "modified_impl", "impacted", "deleted", "unchanged"] as const;

type ChangeFilterOption = {
  count: number;
  key: string;
  label: string;
  statusClass: string | null;
};

type ChangeListProps = {
  changes: SymbolChange[];
  emptyBody: string;
  emptyTitle: string;
  language: LanguageMode;
};

function ChangeList({ changes, emptyBody, emptyTitle, language }: ChangeListProps) {
  const [activeFilter, setActiveFilter] = useState<string>("all");
  const [currentPage, setCurrentPage] = useState(1);

  useEffect(() => {
    setActiveFilter((currentFilter) =>
      currentFilter === "all" || changes.some((change) => change.changeType === currentFilter) ? currentFilter : "all"
    );
  }, [changes]);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeFilter, changes]);

  if (changes.length === 0) {
    return <EmptyState title={emptyTitle} body={emptyBody} />;
  }

  const changeGroups = groupSymbolChanges(changes);
  const filterOptions = buildChangeFilterOptions(changes, changeGroups, language);
  const filteredChanges = activeFilter === "all" ? changes : changeGroups.get(activeFilter) ?? [];
  const pageCount = Math.max(1, Math.ceil(filteredChanges.length / CHANGE_PAGE_SIZE));
  const safePage = Math.min(currentPage, pageCount);
  const pageStart = (safePage - 1) * CHANGE_PAGE_SIZE;
  const visibleChanges = filteredChanges.slice(pageStart, pageStart + CHANGE_PAGE_SIZE);
  const activeFilterLabel =
    filterOptions.find((option) => option.key === activeFilter)?.label ?? getAllChangeFilterLabel(language);

  return (
    <div className="change-list-shell">
      <div className="change-filter-bar" role="tablist" aria-label={language === "zh" ? "变更分类" : "Change categories"}>
        {filterOptions.map((option) => (
          <button
            key={option.key}
            type="button"
            className={`change-filter-pill ${option.statusClass ? `status-${option.statusClass}` : "status-all"} ${
              activeFilter === option.key ? "is-active" : ""
            }`}
            aria-pressed={activeFilter === option.key}
            onClick={() => setActiveFilter(option.key)}
          >
            <span>{option.label}</span>
            <strong>{option.count}</strong>
          </button>
        ))}
      </div>

      <div className="change-page-bar">
        <span className="change-page-summary">
          {formatChangePaginationLabel(language, activeFilterLabel, filteredChanges.length, safePage, pageCount)}
        </span>
        <div className="change-pagination">
          <button
            type="button"
            className="change-page-button"
            aria-label={language === "zh" ? "上一页" : "Previous page"}
            disabled={safePage <= 1}
            onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
          >
            &lt;
          </button>
          <span className="change-page-index">
            {safePage} / {pageCount}
          </span>
          <button
            type="button"
            className="change-page-button"
            aria-label={language === "zh" ? "下一页" : "Next page"}
            disabled={safePage >= pageCount}
            onClick={() => setCurrentPage((page) => Math.min(pageCount, page + 1))}
          >
            &gt;
          </button>
        </div>
      </div>

      <div className="list-stack">
        {visibleChanges.map((change) => (
          <article key={`${change.changeType}:${change.symbolKey}`} className={`change-card status-${change.changeType}`}>
            <div className="change-head">
              <span className={`status-pill status-${change.changeType}`}>
                {formatStatusLabel(change.changeType, language)}
              </span>
              <code>{compactSymbolKey(change.symbolKey)}</code>
            </div>
            <p>{change.reason}</p>
          </article>
        ))}
      </div>
    </div>
  );
}

type EmptyStateProps = {
  title: string;
  body: string;
};

function EmptyState({ title, body }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <p>{body}</p>
    </div>
  );
}

type SelectionCardProps = {
  title: string;
  subtitle: string;
  status: string;
  statusLabel: string;
  kind: string;
  detail: string;
};

function SelectionCard({ title, subtitle, status, statusLabel, kind, detail }: SelectionCardProps) {
  return (
    <article className={`selection-card status-${status}`}>
      <div className="selection-head">
        <span className={`status-pill status-${status}`}>{statusLabel}</span>
        <span className="selection-kind">{kind}</span>
      </div>
      <h3>{title}</h3>
      <code>{subtitle}</code>
      <p>{detail}</p>
    </article>
  );
}

type SnapshotDiagnosticsPanelProps = {
  diagnostics: SnapshotDiagnostics | null;
  emptyBody: string;
  emptyTitle: string;
  labels: SnapshotDiagnosticsCopy;
  language: LanguageMode;
  loading: boolean;
  selectedSnapshot: ProjectSnapshot | null;
};

function SnapshotDiagnosticsPanel({
  diagnostics,
  emptyBody,
  emptyTitle,
  labels,
  language,
  loading,
  selectedSnapshot
}: SnapshotDiagnosticsPanelProps) {
  if (loading) {
    return <EmptyState title={labels.loadingTitle} body={labels.loadingBody} />;
  }

  if (!selectedSnapshot) {
    return <EmptyState title={emptyTitle} body={emptyBody} />;
  }

  if (!diagnostics) {
    return <EmptyState title={labels.unavailableTitle} body={labels.unavailableBody} />;
  }

  return (
    <div className="snapshot-diagnostics-shell">
      <div className="snapshot-diagnostics-grid">
        <DiagnosticFact
          label={labels.baseSnapshot}
          value={diagnostics.baseSnapshotId ? shortId(diagnostics.baseSnapshotId) : labels.none}
        />
        <DiagnosticFact
          label={labels.requestedMode}
          value={formatSnapshotModeLabel(diagnostics.requestedMode, language, labels)}
        />
        <DiagnosticFact
          label={labels.effectiveMode}
          value={formatSnapshotModeLabel(diagnostics.effectiveMode, language, labels)}
        />
        <DiagnosticFact
          label={labels.changeSource}
          value={formatSnapshotChangeSourceLabel(diagnostics.changeSource, language, labels)}
        />
        <DiagnosticFact label={labels.workspaceChanges} value={diagnostics.includesWorkspaceChanges ? labels.yes : labels.no} />
        <DiagnosticFact label={labels.gitCommit} value={diagnostics.gitCommit ? shortId(diagnostics.gitCommit) : labels.none} />
      </div>

      <div className="list-stack">
        <article className="selection-card status-unchanged">
          <div className="selection-head">
            <span className="status-pill status-unchanged">{labels.summary}</span>
          </div>
          <p>{diagnostics.note ?? labels.none}</p>
        </article>

        <article className={`selection-card ${diagnostics.fallbackReason ? "status-modified_impl" : "status-unchanged"}`}>
          <div className="selection-head">
            <span className={`status-pill ${diagnostics.fallbackReason ? "status-modified_impl" : "status-unchanged"}`}>
              {labels.fallbackReason}
            </span>
          </div>
          <p>{diagnostics.fallbackReason ?? labels.none}</p>
        </article>
      </div>

      <DiagnosticPathSection title={labels.changedFiles} paths={diagnostics.changedFiles} emptyLabel={labels.none} />
      <DiagnosticPathSection title={labels.renamedPaths} paths={diagnostics.renamedPaths} emptyLabel={labels.none} />
      <DiagnosticPathSection title={labels.rebuildPaths} paths={diagnostics.rebuildPaths} emptyLabel={labels.none} />
      <DiagnosticPathSection title={labels.removedPaths} paths={diagnostics.removedPaths} emptyLabel={labels.none} />
    </div>
  );
}

type ChangeSetReviewPanelProps = {
  changedFiles: string[] | null;
  emptyBody: string;
  emptyTitle: string;
  language: LanguageMode;
  markdownLabel: string;
  report: ChangeSetReviewMarkdownReport | null;
  result: ChangeSetReviewResult | null;
  reviewSourceLabel: string;
  reviewSourceValue: IndexChangeSource;
  reviewTargetsLabel: string;
};

function ChangeSetReviewPanel({
  changedFiles,
  emptyBody,
  emptyTitle,
  language,
  markdownLabel,
  report,
  result,
  reviewSourceLabel,
  reviewSourceValue,
  reviewTargetsLabel
}: ChangeSetReviewPanelProps) {
  if (!result) {
    return <EmptyState title={emptyTitle} body={emptyBody} />;
  }

  const riskStatusClass = normalizeRiskStatusClass(result.risk.level);

  return (
    <div className="review-report-shell">
      <div className="review-report-meta">
        <DiagnosticFact label={reviewSourceLabel} value={reviewSourceValue === "manual" ? "manual" : "git"} />
        <DiagnosticFact label="Risk" value={`${result.risk.level} (${result.risk.score})`} />
        <DiagnosticFact label="Targets" value={String(result.reviewTargets.length)} />
        <DiagnosticFact label="Files" value={String(result.changedFiles.length)} />
      </div>

      <article className={`selection-card ${riskStatusClass}`}>
        <div className="selection-head">
          <span className={`status-pill ${riskStatusClass}`}>{result.risk.level}</span>
          <span className="selection-kind">{result.snapshotDisplayName}</span>
        </div>
        <h3>{result.summary}</h3>
        <p>{result.note}</p>
      </article>

      <section className="review-report-section">
        <div className="diagnostic-path-header">
          <strong>{reviewTargetsLabel}</strong>
          <span>{result.reviewTargets.length}</span>
        </div>
        <div className="list-stack">
          {result.reviewTargets.map((target) => (
            <article key={`${target.reviewRole}:${target.symbolKey}`} className={`change-card status-${target.status}`}>
              <div className="change-head">
                <span className={`status-pill status-${target.status}`}>{formatStatusLabel(target.status, language)}</span>
                <code>{compactSymbolKey(target.symbolKey)}</code>
              </div>
              <strong>{target.displayName}</strong>
              <p>{target.qualifiedName}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="review-report-section">
        <div className="diagnostic-path-header">
          <strong>Reasons</strong>
          <span>{result.risk.reasons.length}</span>
        </div>
        <ul className="review-report-reasons">
          {result.risk.reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      </section>

      <section className="review-report-section">
        <div className="diagnostic-path-header">
          <strong>Propagation Paths</strong>
          <span>{result.propagationPaths.length}</span>
        </div>
        {result.propagationPaths.length === 0 ? (
          <p className="diagnostic-path-empty">None</p>
        ) : (
          <div className="list-stack">
            {result.propagationPaths.map((path) => (
              <article
                key={`${path.fromSymbol.symbolKey}:${path.toSymbol.symbolKey}:${path.relationType}`}
                className="change-card status-impacted"
              >
                <div className="change-head">
                  <span className="status-pill status-impacted">{formatEdgeTypeLabel(path.relationType, language)}</span>
                  <code>
                    {compactSymbolKey(path.fromSymbol.symbolKey)} -&gt; {compactSymbolKey(path.toSymbol.symbolKey)}
                  </code>
                </div>
                <strong>
                  {path.fromSymbol.displayName} -&gt; {path.toSymbol.displayName}
                </strong>
                <p>
                  {path.fromSymbol.qualifiedName} -&gt; {path.toSymbol.qualifiedName}
                </p>
                {path.filePath ? (
                  <p className="review-path-meta">
                    <code>{path.filePath}</code>
                    {path.sourceLine ? `:${path.sourceLine}` : ""}
                  </p>
                ) : null}
              </article>
            ))}
          </div>
        )}
      </section>

      <DiagnosticPathSection
        title={changedFiles ? "Manual Changed Files" : "Changed Files"}
        paths={result.changedFiles}
        emptyLabel="None"
      />

      {report ? (
        <section className="review-report-section">
          <div className="diagnostic-path-header">
            <strong>{markdownLabel}</strong>
            <span>{report.fileName}</span>
          </div>
          <pre className="review-markdown-preview">{report.markdown}</pre>
        </section>
      ) : null}
    </div>
  );
}

function DiagnosticFact({ label, value }: { label: string; value: string }) {
  return (
    <article className="diagnostic-fact">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function DiagnosticPathSection({ title, paths, emptyLabel }: { title: string; paths: string[]; emptyLabel: string }) {
  return (
    <section className="diagnostic-path-section">
      <div className="diagnostic-path-header">
        <strong>{title}</strong>
        <span>{paths.length}</span>
      </div>
      {paths.length === 0 ? (
        <p className="diagnostic-path-empty">{emptyLabel}</p>
      ) : (
        <ul className="diagnostic-path-list">
          {paths.map((path) => (
            <li key={`${title}:${path}`}>
              <code>{path}</code>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

type ContextMenuItem = {
  danger?: boolean;
  disabled?: boolean;
  label: string;
  onSelect: () => void;
};

type ContextMenuProps = {
  items: ContextMenuItem[];
  x: number;
  y: number;
  onClose: () => void;
};

function ContextMenu({ items, x, y, onClose }: ContextMenuProps) {
  return (
    <div className="context-menu-layer" onClick={onClose}>
      <div
        className="context-menu"
        style={{ left: x, top: y }}
        onClick={(event) => event.stopPropagation()}
        onContextMenu={(event) => event.preventDefault()}
      >
        {items.map((item) => (
          <button
            key={item.label}
            type="button"
            className={`context-menu-item ${item.danger ? "is-danger" : ""}`}
            disabled={item.disabled}
            onClick={() => {
              onClose();
              item.onSelect();
            }}
          >
            {item.label}
          </button>
        ))}
      </div>
    </div>
  );
}

function filterClassGraph(
  graph: ClassGraph | null,
  searchQuery: string,
  displayMode: ClassGraphDisplayMode
) {
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

function isIncrementalClassNode(node: GraphNode) {
  return node.status.toLowerCase() !== "unchanged";
}

function getClassGraphSearchLabel(language: LanguageMode) {
  return language === "zh" ? "节点检索" : "Search nodes";
}

function getClassGraphSearchPlaceholder(language: LanguageMode) {
  return language === "zh" ? "按类名或限定名模糊匹配" : "Fuzzy match by class name or qualified name";
}

function getClassGraphDisplayLabel(language: LanguageMode) {
  return language === "zh" ? "显示范围" : "Display";
}

function getClassGraphOverlayToggleLabel(language: LanguageMode, isCollapsed: boolean) {
  if (language === "zh") {
    return isCollapsed ? "\u5c55\u5f00\u7b5b\u9009" : "\u6536\u8d77\u7b5b\u9009";
  }
  return isCollapsed ? "Show filters" : "Hide filters";
}

function formatClassGraphEdgeType(edgeType: string, language: LanguageMode) {
  if (edgeType.toLowerCase() === "uses_type") {
    return language === "zh" ? "依赖" : "dependency";
  }
  return formatEdgeTypeLabel(edgeType, language);
}

function resolveClassGraphEmptyState(
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

function toMessage(error: unknown, fallbackMessage: string, codeMessages?: Record<string, string>) {
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

function shortId(value: string) {
  return value.slice(0, 8);
}

function resolveSnapshotDisplayName(snapshot: ProjectSnapshot) {
  return snapshot.displayName === snapshot.id ? shortId(snapshot.displayName) : snapshot.displayName;
}

function resolveContextMenuPosition(clientX: number, clientY: number) {
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

function compactSymbolKey(symbolKey: string) {
  const methodSeparatorIndex = symbolKey.indexOf("#");
  if (methodSeparatorIndex >= 0) {
    return symbolKey.slice(methodSeparatorIndex + 1);
  }
  const typeSeparatorIndex = symbolKey.lastIndexOf(":");
  return typeSeparatorIndex >= 0 ? symbolKey.slice(typeSeparatorIndex + 1) : symbolKey;
}

function normalizeRiskStatusClass(riskLevel: string) {
  switch (riskLevel.toLowerCase()) {
    case "high":
      return "status-modified_api";
    case "medium":
      return "status-impacted";
    default:
      return "status-added";
  }
}

function downloadMarkdownReport(report: ChangeSetReviewMarkdownReport) {
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

function groupSymbolChanges(changes: SymbolChange[]) {
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

function buildChangeFilterOptions(
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

function getAllChangeFilterLabel(language: LanguageMode) {
  return language === "zh" ? "全部" : "All";
}

function formatChangePaginationLabel(
  language: LanguageMode,
  filterLabel: string,
  filteredCount: number,
  currentPage: number,
  pageCount: number
) {
  if (language === "zh") {
    return `${filterLabel} · 共 ${filteredCount} 条 · 第 ${currentPage}/${pageCount} 页`;
  }
  return `${filterLabel} · ${filteredCount} items · Page ${currentPage}/${pageCount}`;
}

function parseChangedFilesText(changedFilesText: string) {
  return changedFilesText
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildSnapshotGroups(snapshots: ProjectSnapshot[], language: LanguageMode) {
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

function getUncommittedLabel(language: LanguageMode) {
  return language === "zh" ? "未 commit" : "Uncommitted";
}

function findSnapshotGroupKey(snapshots: ProjectSnapshot[], snapshotId: string) {
  const snapshot = snapshots.find((item) => item.id === snapshotId);
  if (!snapshot) {
    return null;
  }
  return snapshot.gitCommit ? `commit:${snapshot.gitCommit}` : "uncommitted";
}

function getUncommittedSnapshotGroupLabel(language: LanguageMode) {
  return language === "zh" ? "\u672a commit" : "Uncommitted";
}

function buildGraphSceneStorageKey(kind: "class" | "method", projectId: string, snapshotId: string, classId?: string) {
  return classId ? `${kind}:${projectId}:${snapshotId}:${classId}` : `${kind}:${projectId}:${snapshotId}`;
}

type SnapshotGroup = {
  key: string;
  title: string;
  shortCommit: string | null;
  snapshots: ProjectSnapshot[];
};

type SnapshotDiagnosticsCopy = {
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

function formatSnapshotModeLabel(mode: string | null, language: LanguageMode, copy: SnapshotDiagnosticsCopy) {
  const normalizedMode = mode?.toLowerCase();
  if (normalizedMode === "incremental") {
    return language === "zh" ? "增量" : "Incremental";
  }
  if (normalizedMode === "full") {
    return language === "zh" ? "全量" : "Full";
  }
  return copy.none;
}

function formatSnapshotChangeSourceLabel(
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

function getSnapshotDiagnosticsCopy(language: LanguageMode): SnapshotDiagnosticsCopy {
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

function getSnapshotUiCopy(language: LanguageMode) {
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

function getContextMenuCopy(language: LanguageMode) {
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

export default App;
