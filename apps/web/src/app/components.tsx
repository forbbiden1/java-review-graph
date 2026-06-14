import { type ReactNode, useEffect, useState } from "react";
import type {
  ChangeSetReviewMarkdownReport,
  ChangeSetReviewResult,
  ProjectSnapshot,
  SnapshotCompareResult,
  SnapshotDiagnostics,
  SymbolChange
} from "../api/client";
import { formatEdgeTypeLabel, formatStatusLabel } from "../i18n";
import type { LanguageMode } from "../platform";
import type { IndexChangeSource, SnapshotDiagnosticsCopy } from "./view-model";
import {
  buildChangeFilterOptions,
  compactSymbolKey,
  formatChangePaginationLabel,
  formatSnapshotChangeSourceLabel,
  formatSnapshotModeLabel,
  getAllChangeFilterLabel,
  groupSymbolChanges,
  normalizeRiskStatusClass,
  shortId
} from "./utils";

const CHANGE_PAGE_SIZE = 6;

export type PanelProps = {
  actions?: ReactNode;
  children: ReactNode;
  subtitle: string;
  title: string;
};

export function Panel({ actions, title, subtitle, children }: PanelProps) {
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

export function MetricBadge({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-badge">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

type ChangeListProps = {
  changes: SymbolChange[];
  emptyBody: string;
  emptyTitle: string;
  language: LanguageMode;
};

export function ChangeList({ changes, emptyBody, emptyTitle, language }: ChangeListProps) {
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
              <span className={`status-pill status-${change.changeType}`}>{formatStatusLabel(change.changeType, language)}</span>
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

export function EmptyState({ title, body }: EmptyStateProps) {
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

export function SelectionCard({ title, subtitle, status, statusLabel, kind, detail }: SelectionCardProps) {
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

export function SnapshotDiagnosticsPanel({
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
        <DiagnosticFact label={labels.baseSnapshot} value={diagnostics.baseSnapshotId ? shortId(diagnostics.baseSnapshotId) : labels.none} />
        <DiagnosticFact label={labels.requestedMode} value={formatSnapshotModeLabel(diagnostics.requestedMode, language, labels)} />
        <DiagnosticFact label={labels.effectiveMode} value={formatSnapshotModeLabel(diagnostics.effectiveMode, language, labels)} />
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

type SnapshotComparePanelProps = {
  baseSnapshotId: string;
  compareLabel: string;
  compareResult: SnapshotCompareResult | null;
  emptyBody: string;
  emptyTitle: string;
  language: LanguageMode;
  loading: boolean;
  onBaseSnapshotChange: (snapshotId: string) => void;
  onRunCompare: () => void;
  runLabel: string;
  snapshots: ProjectSnapshot[];
  targetSnapshot: ProjectSnapshot | null;
};

export function SnapshotComparePanel({
  baseSnapshotId,
  compareLabel,
  compareResult,
  emptyBody,
  emptyTitle,
  language,
  loading,
  onBaseSnapshotChange,
  onRunCompare,
  runLabel,
  snapshots,
  targetSnapshot
}: SnapshotComparePanelProps) {
  const baseOptions = snapshots.filter((snapshot) => snapshot.id !== targetSnapshot?.id);

  if (!targetSnapshot || snapshots.length < 2) {
    return <EmptyState title={emptyTitle} body={emptyBody} />;
  }

  return (
    <div className="snapshot-compare-shell">
      <div className="snapshot-compare-controls">
        <label className="field">
          <span>{compareLabel}</span>
          <select value={baseSnapshotId} onChange={(event) => onBaseSnapshotChange(event.target.value)}>
            <option value="">{language === "zh" ? "选择基线快照" : "Choose base snapshot"}</option>
            {baseOptions.map((snapshot) => (
              <option key={snapshot.id} value={snapshot.id}>
                {snapshot.displayName}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="secondary-button" disabled={loading || !baseSnapshotId} onClick={onRunCompare}>
          {loading ? (language === "zh" ? "对比中..." : "Comparing...") : runLabel}
        </button>
      </div>

      {compareResult ? (
        <>
          <div className="snapshot-compare-summary">
            <DiagnosticFact label="Added" value={String(compareResult.summary.added)} />
            <DiagnosticFact label="Deleted" value={String(compareResult.summary.deleted)} />
            <DiagnosticFact label="API" value={String(compareResult.summary.modifiedApi)} />
            <DiagnosticFact label="Impl" value={String(compareResult.summary.modifiedImpl)} />
          </div>
          <article className="selection-card status-impacted">
            <div className="selection-head">
              <span className="status-pill status-impacted">{compareResult.summary.changed}</span>
              <span className="selection-kind">
                {compareResult.baseSnapshot.displayName} -&gt; {compareResult.targetSnapshot.displayName}
              </span>
            </div>
            <h3>{language === "zh" ? "快照差异摘要" : "Snapshot Diff Summary"}</h3>
            <p>{compareResult.note}</p>
          </article>
          <div className="list-stack">
            {compareResult.changes.slice(0, 8).map((change) => (
              <article key={`${change.changeType}:${change.symbolKey}`} className={`change-card status-${change.changeType}`}>
                <div className="change-head">
                  <span className={`status-pill status-${change.changeType}`}>{formatStatusLabel(change.changeType, language)}</span>
                  <code>{compactSymbolKey(change.symbolKey)}</code>
                </div>
                <strong>{change.displayName}</strong>
                <p>{change.qualifiedName}</p>
                {change.filePath ? (
                  <p className="review-path-meta">
                    <code>{change.filePath}</code>
                  </p>
                ) : null}
              </article>
            ))}
          </div>
        </>
      ) : null}
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

export function ChangeSetReviewPanel({
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

      <section className="review-report-section">
        <div className="diagnostic-path-header">
          <strong>Test Focus Suggestions</strong>
          <span>{result.testFocusSuggestions.length}</span>
        </div>
        {result.testFocusSuggestions.length === 0 ? (
          <p className="diagnostic-path-empty">None</p>
        ) : (
          <div className="list-stack">
            {result.testFocusSuggestions.map((suggestion) => (
              <article key={`${suggestion.symbol.symbolKey}:${suggestion.priority}`} className={`change-card status-${suggestion.symbol.status}`}>
                <div className="change-head">
                  <span className={`status-pill status-${suggestion.symbol.status}`}>{suggestion.priority}</span>
                  <code>{compactSymbolKey(suggestion.symbol.symbolKey)}</code>
                </div>
                <strong>{suggestion.symbol.displayName}</strong>
                <p>{suggestion.reason}</p>
              </article>
            ))}
          </div>
        )}
      </section>

      <DiagnosticPathSection title={changedFiles ? "Manual Changed Files" : "Changed Files"} paths={result.changedFiles} emptyLabel="None" />

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

export function DiagnosticFact({ label, value }: { label: string; value: string }) {
  return (
    <article className="diagnostic-fact">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

export function DiagnosticPathSection({ title, paths, emptyLabel }: { title: string; paths: string[]; emptyLabel: string }) {
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

export type ContextMenuItem = {
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

export function ContextMenu({ items, x, y, onClose }: ContextMenuProps) {
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
