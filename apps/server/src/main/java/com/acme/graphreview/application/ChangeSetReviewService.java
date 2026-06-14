package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.GitChangedFiles;
import com.acme.graphreview.infrastructure.GitSnapshotMetadataResolver;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.SymbolRecord;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ChangeSetReviewService {

    private final ProjectService projectService;
    private final SnapshotRepository snapshotRepository;
    private final SourceFileRepository sourceFileRepository;
    private final SymbolRepository symbolRepository;
    private final SymbolChangeRepository symbolChangeRepository;
    private final GitSnapshotMetadataResolver gitSnapshotMetadataResolver;

    public ChangeSetReviewService(
            ProjectService projectService,
            SnapshotRepository snapshotRepository,
            SourceFileRepository sourceFileRepository,
            SymbolRepository symbolRepository,
            SymbolChangeRepository symbolChangeRepository,
            GitSnapshotMetadataResolver gitSnapshotMetadataResolver
    ) {
        this.projectService = projectService;
        this.snapshotRepository = snapshotRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.symbolRepository = symbolRepository;
        this.symbolChangeRepository = symbolChangeRepository;
        this.gitSnapshotMetadataResolver = gitSnapshotMetadataResolver;
    }

    public ChangeSetReviewResult reviewChangeSet(String projectId, ChangeSetReviewCommand command) {
        RegisteredProject project = projectService.getProject(projectId);
        ProjectSnapshot snapshot = resolveSnapshot(project.id(), command.snapshotId());
        GitChangedFiles changedFiles = resolveChangedFiles(project, snapshot, command);

        LinkedHashSet<String> normalizedPaths = new LinkedHashSet<>();
        for (String rawPath : changedFiles.paths()) {
            String normalizedPath = normalizeProjectPath(project.rootPath(), rawPath);
            if (normalizedPath != null) {
                normalizedPaths.add(normalizedPath);
            }
        }

        List<SymbolRecord> symbols = symbolRepository.findByProjectIdAndSnapshotId(project.id(), snapshot.id());
        Map<String, SymbolRecord> symbolByKey = symbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left, LinkedHashMap::new));

        LinkedHashSet<String> changedSymbolKeys = symbols.stream()
                .filter(symbol -> normalizedPaths.contains(symbol.filePath()))
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<StoredSymbolChange> persistedChanges = symbolChangeRepository.findByProjectIdAndSnapshotId(project.id(), snapshot.id());
        LinkedHashSet<String> impactedSymbolKeys = persistedChanges.stream()
                .filter(change -> "impacted".equalsIgnoreCase(change.changeType()) || "deleted".equalsIgnoreCase(change.changeType()))
                .map(StoredSymbolChange::symbolKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<ChangeSetReviewSymbol> changedSymbols = changedSymbolKeys.stream()
                .map(symbolByKey::get)
                .filter(java.util.Objects::nonNull)
                .map(symbol -> ChangeSetReviewSymbol.from(symbol, "changed"))
                .toList();
        List<ChangeSetReviewSymbol> impactedSymbols = impactedSymbolKeys.stream()
                .map(symbolByKey::get)
                .filter(java.util.Objects::nonNull)
                .filter(symbol -> !changedSymbolKeys.contains(symbol.symbolKey()))
                .map(symbol -> ChangeSetReviewSymbol.from(symbol, "impacted"))
                .toList();

        ChangeSetRiskSummary riskSummary = scoreRisk(changedSymbols, impactedSymbols, persistedChanges, normalizedPaths.size());
        List<ChangeSetReviewSymbol> reviewTargets = buildReviewTargets(changedSymbols, impactedSymbols);
        String summary = buildSummary(
                normalizedPaths.size(),
                changedSymbols.size(),
                impactedSymbols.size(),
                riskSummary.riskLevel(),
                snapshot.displayName()
        );

        return new ChangeSetReviewResult(
                project.id(),
                snapshot.id(),
                snapshot.displayName(),
                changedFiles.note(),
                List.copyOf(normalizedPaths),
                changedFiles.renamedPaths(),
                changedFiles.includesWorkspaceChanges(),
                changedSymbols,
                impactedSymbols,
                reviewTargets,
                riskSummary,
                summary
        );
    }

    public ChangeSetReviewMarkdownReport exportMarkdownReport(String projectId, ChangeSetReviewCommand command) {
        String changeSource = normalizeChangeSource(command.changeSource());
        ChangeSetReviewResult result = reviewChangeSet(projectId, command);
        return new ChangeSetReviewMarkdownReport(
                buildReportFileName(result, changeSource),
                buildMarkdownReport(result, changeSource)
        );
    }

    private ProjectSnapshot resolveSnapshot(String projectId, String snapshotId) {
        if (snapshotId != null && !snapshotId.isBlank()) {
            return snapshotRepository.findByProjectIdAndSnapshotId(projectId, snapshotId)
                    .orElseThrow(() -> new SnapshotNotFoundException(projectId));
        }
        return snapshotRepository.findLatestByProjectId(projectId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
    }

    private GitChangedFiles resolveChangedFiles(RegisteredProject project, ProjectSnapshot snapshot, ChangeSetReviewCommand command) {
        String changeSource = normalizeChangeSource(command.changeSource());
        if ("manual".equals(changeSource)) {
            if (command.changedFiles() == null || command.changedFiles().isEmpty()) {
                throw new ProjectValidationException("Manual change-set review requires at least one changed file.");
            }
            return GitChangedFiles.available(command.changedFiles(), "Manual change-set review collected " + command.changedFiles().size() + " changed path(s).");
        }
        return gitSnapshotMetadataResolver.resolveChangedFiles(Path.of(project.rootPath()), snapshot.gitCommit());
    }

    private String normalizeChangeSource(String rawChangeSource) {
        if (rawChangeSource == null || rawChangeSource.isBlank()) {
            return "git";
        }
        String normalized = rawChangeSource.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("git", "manual").contains(normalized)) {
            throw new ProjectValidationException("Unsupported change-set review source: " + rawChangeSource);
        }
        return normalized;
    }

    private String normalizeProjectPath(String projectRootPath, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path rootPath = Path.of(projectRootPath);
        Path candidatePath = Path.of(rawPath);
        Path absolutePath = candidatePath.isAbsolute() ? candidatePath.normalize() : rootPath.resolve(candidatePath).normalize();
        if (!absolutePath.startsWith(rootPath)) {
            return candidatePath.toString().replace('\\', '/');
        }
        return rootPath.relativize(absolutePath).toString().replace('\\', '/');
    }

    private ChangeSetRiskSummary scoreRisk(
            List<ChangeSetReviewSymbol> changedSymbols,
            List<ChangeSetReviewSymbol> impactedSymbols,
            List<StoredSymbolChange> persistedChanges,
            int changedFileCount
    ) {
        int riskScore = 0;
        LinkedHashSet<String> reasons = new LinkedHashSet<>();

        boolean publicApiChanged = changedSymbols.stream()
                .anyMatch(symbol -> "modified_api".equals(symbol.status()) || "deleted".equals(symbol.status()));
        if (publicApiChanged) {
            riskScore += 3;
            reasons.add("Public API or deleted symbol changed.");
        }

        if (impactedSymbols.size() >= 3) {
            riskScore += 2;
            reasons.add("At least 3 impacted symbols were found.");
        } else if (!impactedSymbols.isEmpty()) {
            riskScore += 1;
            reasons.add("One-hop impacted symbols were found.");
        }

        boolean deletedSymbols = persistedChanges.stream()
                .anyMatch(change -> "deleted".equalsIgnoreCase(change.changeType()));
        if (deletedSymbols) {
            riskScore += 2;
            reasons.add("Deleted symbols require extra review.");
        }

        if (changedFileCount > 0 && changedSymbols.isEmpty()) {
            riskScore += 1;
            reasons.add("Changed files did not map to indexed symbols.");
        }

        if (reasons.isEmpty()) {
            reasons.add("Only implementation-local changes were found in the selected snapshot.");
        }

        String riskLevel;
        if (riskScore >= 5) {
            riskLevel = "high";
        } else if (riskScore >= 2) {
            riskLevel = "medium";
        } else {
            riskLevel = "low";
        }

        return new ChangeSetRiskSummary(riskLevel, riskScore, List.copyOf(reasons));
    }

    private List<ChangeSetReviewSymbol> buildReviewTargets(
            List<ChangeSetReviewSymbol> changedSymbols,
            List<ChangeSetReviewSymbol> impactedSymbols
    ) {
        return java.util.stream.Stream.concat(changedSymbols.stream(), impactedSymbols.stream())
                .sorted((left, right) -> Integer.compare(reviewPriority(right), reviewPriority(left)))
                .limit(5)
                .toList();
    }

    private int reviewPriority(ChangeSetReviewSymbol symbol) {
        int priority = switch (symbol.status()) {
            case "deleted" -> 5;
            case "modified_api" -> 4;
            case "impacted" -> 3;
            case "added" -> 2;
            case "modified_impl" -> 1;
            default -> 0;
        };
        return priority + ("changed".equals(symbol.reviewRole()) ? 2 : 0);
    }

    private String buildSummary(
            int changedFileCount,
            int changedSymbolCount,
            int impactedSymbolCount,
            String riskLevel,
            String snapshotDisplayName
    ) {
        return "Change-set review for snapshot \"" + snapshotDisplayName + "\" found "
                + changedFileCount + " changed file(s), "
                + changedSymbolCount + " changed symbol(s), and "
                + impactedSymbolCount + " impacted symbol(s). Risk level: " + riskLevel + ".";
    }

    private String buildReportFileName(ChangeSetReviewResult result, String changeSource) {
        String projectSlug = slugify(result.projectId());
        String snapshotSlug = slugify(result.snapshotDisplayName());
        if (snapshotSlug.isBlank()) {
            snapshotSlug = slugify(result.snapshotId());
        }
        return "change-set-review-" + projectSlug + "-" + snapshotSlug + "-" + changeSource + ".md";
    }

    private String buildMarkdownReport(ChangeSetReviewResult result, String changeSource) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Change-Set Review Report\n\n");
        markdown.append("## Scope\n\n");
        markdown.append("- Project: `").append(result.projectId()).append("`\n");
        markdown.append("- Snapshot: `").append(result.snapshotDisplayName()).append("` (`").append(result.snapshotId()).append("`)\n");
        markdown.append("- Change Source: `").append(changeSource).append("`\n");
        markdown.append("- Includes Workspace Changes: `").append(result.includesWorkspaceChanges()).append("`\n");
        if (result.note() != null && !result.note().isBlank()) {
            markdown.append("- Collection Note: ").append(result.note()).append("\n");
        }
        markdown.append("\n## Summary\n\n");
        markdown.append(result.summary()).append("\n\n");
        markdown.append("## Risk\n\n");
        markdown.append("- Level: `").append(result.risk().riskLevel()).append("`\n");
        markdown.append("- Score: `").append(result.risk().riskScore()).append("`\n");
        markdown.append("- Reasons:\n");
        for (String reason : result.risk().reasons()) {
            markdown.append("  - ").append(reason).append("\n");
        }
        markdown.append("\n");
        appendPathSection(markdown, "Changed Files", result.changedFiles());
        appendPathSection(markdown, "Renamed Paths", result.renamedPaths());
        appendSymbolSection(markdown, "Prioritized Review Targets", result.reviewTargets());
        appendSymbolSection(markdown, "Changed Symbols", result.changedSymbols());
        appendSymbolSection(markdown, "Impacted Symbols", result.impactedSymbols());
        return markdown.toString();
    }

    private void appendPathSection(StringBuilder markdown, String title, List<String> paths) {
        markdown.append("## ").append(title).append("\n\n");
        if (paths.isEmpty()) {
            markdown.append("- None\n\n");
            return;
        }
        for (String path : paths) {
            markdown.append("- `").append(path).append("`\n");
        }
        markdown.append("\n");
    }

    private void appendSymbolSection(StringBuilder markdown, String title, List<ChangeSetReviewSymbol> symbols) {
        markdown.append("## ").append(title).append("\n\n");
        if (symbols.isEmpty()) {
            markdown.append("- None\n\n");
            return;
        }
        for (ChangeSetReviewSymbol symbol : symbols) {
            markdown.append("- `").append(symbol.qualifiedName()).append("`");
            markdown.append(" (`").append(symbol.kind()).append("`, `").append(symbol.status()).append("`, `").append(symbol.reviewRole()).append("`)");
            markdown.append(" - `").append(symbol.symbolKey()).append("`\n");
        }
        markdown.append("\n");
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    public record ChangeSetReviewCommand(
            String snapshotId,
            String changeSource,
            List<String> changedFiles
    ) {
    }

    public record ChangeSetReviewResult(
            String projectId,
            String snapshotId,
            String snapshotDisplayName,
            String note,
            List<String> changedFiles,
            List<String> renamedPaths,
            boolean includesWorkspaceChanges,
            List<ChangeSetReviewSymbol> changedSymbols,
            List<ChangeSetReviewSymbol> impactedSymbols,
            List<ChangeSetReviewSymbol> reviewTargets,
            ChangeSetRiskSummary risk,
            String summary
    ) {
    }

    public record ChangeSetRiskSummary(
            String riskLevel,
            int riskScore,
            List<String> reasons
    ) {
    }

    public record ChangeSetReviewMarkdownReport(
            String fileName,
            String markdown
    ) {
    }

    public record ChangeSetReviewSymbol(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String kind,
            String status,
            String reviewRole
    ) {
        private static ChangeSetReviewSymbol from(SymbolRecord symbol, String reviewRole) {
            return new ChangeSetReviewSymbol(
                    symbol.symbolKey(),
                    symbol.qualifiedName(),
                    symbol.displayName(),
                    symbol.kind().name().toLowerCase(Locale.ROOT),
                    symbol.changeStatus().name().toLowerCase(Locale.ROOT),
                    reviewRole
            );
        }
    }
}
