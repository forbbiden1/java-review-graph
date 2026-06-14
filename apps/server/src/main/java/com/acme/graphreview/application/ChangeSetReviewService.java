package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.GitChangedFiles;
import com.acme.graphreview.infrastructure.GitSnapshotMetadataResolver;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.review.ChangeStatus;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

        List<StoredSourceFile> storedFiles = sourceFileRepository.findByProjectIdAndSnapshotId(project.id(), snapshot.id());
        Map<String, StoredSourceFile> fileByPath = storedFiles.stream()
                .collect(java.util.stream.Collectors.toMap(StoredSourceFile::path, file -> file, (left, right) -> left, LinkedHashMap::new));

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

        String summary = buildSummary(
                normalizedPaths.size(),
                changedSymbols.size(),
                impactedSymbols.size(),
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
                summary
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

    private String buildSummary(int changedFileCount, int changedSymbolCount, int impactedSymbolCount, String snapshotDisplayName) {
        return "Change-set review for snapshot \"" + snapshotDisplayName + "\" found "
                + changedFileCount + " changed file(s), "
                + changedSymbolCount + " changed symbol(s), and "
                + impactedSymbolCount + " impacted symbol(s).";
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
            String summary
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
