package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.graphreview.infrastructure.GitChangedFiles;
import com.acme.graphreview.infrastructure.GitSnapshotMetadataResolver;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.SymbolRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class IncrementalPlanner {

    private final GitSnapshotMetadataResolver gitSnapshotMetadataResolver;

    public IncrementalPlanner(GitSnapshotMetadataResolver gitSnapshotMetadataResolver) {
        this.gitSnapshotMetadataResolver = gitSnapshotMetadataResolver;
    }

    public ResolvedChangedFiles resolveChangedFiles(
            Path rootPath,
            boolean incremental,
            IncrementalChangeSource incrementalChangeSource,
            List<String> requestedChangedFiles,
            ProjectSnapshot previousSnapshot
    ) {
        if (!incremental) {
            return new ResolvedChangedFiles(List.of(), List.of(), null, false);
        }

        if (incrementalChangeSource == IncrementalChangeSource.MANUAL) {
            if (requestedChangedFiles.isEmpty()) {
                throw new ProjectValidationException("Manual incremental index mode requires at least one changed file.");
            }
            return new ResolvedChangedFiles(requestedChangedFiles, List.of(), null, false);
        }

        GitChangedFiles gitChangedFiles = gitSnapshotMetadataResolver.resolveChangedFiles(
                rootPath,
                previousSnapshot == null ? null : previousSnapshot.gitCommit()
        );
        if (!gitChangedFiles.available()) {
            throw new ProjectValidationException(gitChangedFiles.note());
        }
        return new ResolvedChangedFiles(
                gitChangedFiles.paths(),
                gitChangedFiles.renamedPaths(),
                gitChangedFiles.note(),
                gitChangedFiles.includesWorkspaceChanges()
        );
    }

    public IncrementalPlan buildIncrementalPlan(
            ProjectDescriptor descriptor,
            boolean incrementalRequested,
            List<String> changedFiles,
            List<String> renamedPaths,
            String changedFilesNotePrefix,
            String previousSnapshotId,
            List<StoredSourceFile> previousFiles,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> previousRelations
    ) {
        LinkedHashSet<String> normalizedChangedPaths = normalizeChangedPaths(descriptor.rootPath(), changedFiles);
        if (!incrementalRequested) {
            return IncrementalPlan.full(List.of(), List.of(), null, null);
        }
        if (previousSnapshotId == null) {
            return IncrementalPlan.full(
                    List.copyOf(normalizedChangedPaths),
                    List.copyOf(renamedPaths),
                    withIncrementalNotePrefix(
                            changedFilesNotePrefix,
                            "Incremental fallback: no previous snapshot was available, so a full scan was executed."
                    ),
                    "No previous snapshot was available."
            );
        }

        boolean buildMetadataChanged = normalizedChangedPaths.stream().anyMatch(this::isBuildMetadataPath);
        if (buildMetadataChanged) {
            return IncrementalPlan.full(
                    List.copyOf(normalizedChangedPaths),
                    List.copyOf(renamedPaths),
                    withIncrementalNotePrefix(
                            changedFilesNotePrefix,
                            "Incremental fallback: build configuration changed, so a full scan was executed."
                    ),
                    "Build configuration changed."
            );
        }

        LinkedHashSet<String> changedJavaPaths = normalizedChangedPaths.stream()
                .filter(this::isJavaSourcePath)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (changedJavaPaths.isEmpty()) {
            return IncrementalPlan.incremental(
                    List.copyOf(normalizedChangedPaths),
                    List.copyOf(renamedPaths),
                    List.of(),
                    Set.of(),
                    List.of(),
                    withIncrementalNotePrefix(
                            changedFilesNotePrefix,
                            "Incremental request contained no Java source changes; reused previous snapshot data."
                    )
            );
        }

        Map<String, StoredSourceFile> previousFilesByPath = previousFiles.stream()
                .collect(Collectors.toMap(StoredSourceFile::path, file -> file, (left, right) -> left));
        LinkedHashSet<String> seedPaths = new LinkedHashSet<>(changedJavaPaths);
        Map<String, List<SymbolRecord>> previousSymbolsByFilePath = previousSymbols.stream()
                .collect(Collectors.groupingBy(SymbolRecord::filePath));
        Set<String> seedSymbolKeys = seedPaths.stream()
                .map(previousSymbolsByFilePath::get)
                .filter(java.util.Objects::nonNull)
                .flatMap(Collection::stream)
                .map(SymbolRecord::symbolKey)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, SymbolRecord> previousSymbolByKey = previousSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        LinkedHashSet<String> expandedPaths = new LinkedHashSet<>(seedPaths);
        for (RelationRecord relation : previousRelations) {
            if (!seedSymbolKeys.contains(relation.sourceSymbolKey()) && !seedSymbolKeys.contains(relation.targetSymbolKey())) {
                continue;
            }

            String sourceFilePath = relationSourcePath(relation, previousSymbolByKey);
            String targetFilePath = previousSymbolByKey.containsKey(relation.targetSymbolKey())
                    ? previousSymbolByKey.get(relation.targetSymbolKey()).filePath()
                    : null;
            if (sourceFilePath != null) {
                expandedPaths.add(sourceFilePath);
            }
            if (targetFilePath != null) {
                expandedPaths.add(targetFilePath);
            }
        }

        LinkedHashSet<String> rebuildPaths = expandedPaths.stream()
                .filter(path -> fileExists(descriptor.rootPath(), path))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> replacedPaths = expandedPaths.stream()
                .filter(path -> previousFilesByPath.containsKey(path) || rebuildPaths.contains(path))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> removedPaths = replacedPaths.stream()
                .filter(path -> !rebuildPaths.contains(path))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String note = withIncrementalNotePrefix(
                changedFilesNotePrefix,
                buildIncrementalPlanNote(rebuildPaths, replacedPaths)
        );
        return IncrementalPlan.incremental(
                List.copyOf(normalizedChangedPaths),
                List.copyOf(renamedPaths),
                List.copyOf(rebuildPaths),
                Set.copyOf(replacedPaths),
                List.copyOf(removedPaths),
                note
        );
    }

    public String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new ProjectValidationException("Snapshot display name must not be blank.");
        }
        return displayName.trim();
    }

    public boolean shouldStoreSnapshotAsUncommitted(
            boolean incremental,
            IncrementalChangeSource incrementalChangeSource,
            boolean includesWorkspaceChanges
    ) {
        return incremental
                && incrementalChangeSource == IncrementalChangeSource.GIT
                && includesWorkspaceChanges;
    }

    private String relationSourcePath(RelationRecord relation, Map<String, SymbolRecord> symbolByKey) {
        if (relation.filePath() != null && !relation.filePath().isBlank()) {
            return relation.filePath();
        }
        SymbolRecord source = symbolByKey.get(relation.sourceSymbolKey());
        return source == null ? null : source.filePath();
    }

    private String normalizeChangedPath(Path rootPath, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        Path candidatePath = Path.of(rawPath);
        Path absolutePath = candidatePath.isAbsolute() ? candidatePath.normalize() : rootPath.resolve(candidatePath).normalize();
        if (!absolutePath.startsWith(rootPath)) {
            return null;
        }
        return rootPath.relativize(absolutePath).toString().replace('\\', '/');
    }

    private LinkedHashSet<String> normalizeChangedPaths(Path rootPath, List<String> changedFiles) {
        LinkedHashSet<String> normalizedChangedPaths = new LinkedHashSet<>();
        for (String changedFile : changedFiles) {
            String normalizedPath = normalizeChangedPath(rootPath, changedFile);
            if (normalizedPath != null) {
                normalizedChangedPaths.add(normalizedPath);
            }
        }
        return normalizedChangedPaths;
    }

    private boolean fileExists(Path rootPath, String relativePath) {
        Path filePath = rootPath.resolve(relativePath).normalize();
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    private boolean isJavaSourcePath(String path) {
        return path.endsWith(".java");
    }

    private boolean isBuildMetadataPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.endsWith("/pom.xml")
                || normalized.equals("pom.xml")
                || normalized.endsWith("/build.gradle")
                || normalized.equals("build.gradle")
                || normalized.endsWith("/build.gradle.kts")
                || normalized.equals("build.gradle.kts")
                || normalized.endsWith("/settings.gradle")
                || normalized.equals("settings.gradle")
                || normalized.endsWith("/settings.gradle.kts")
                || normalized.equals("settings.gradle.kts");
    }

    private String buildIncrementalPlanNote(Set<String> rebuildPaths, Set<String> replacedPaths) {
        long removedPathCount = replacedPaths.stream().filter(path -> !rebuildPaths.contains(path)).count();
        if (rebuildPaths.isEmpty() && removedPathCount == 0) {
            return "Incremental request contained no Java source changes; reused previous snapshot data.";
        }
        return "Incremental snapshot rebuilt " + rebuildPaths.size() + " Java file(s)"
                + (removedPathCount > 0 ? " and removed " + removedPathCount + " file(s)." : ".");
    }

    public String withIncrementalNotePrefix(String notePrefix, String detail) {
        if (notePrefix == null || notePrefix.isBlank()) {
            return detail;
        }
        if (detail == null || detail.isBlank()) {
            return notePrefix;
        }
        return notePrefix + " " + detail;
    }

    public enum IncrementalChangeSource {
        GIT,
        MANUAL
    }

    public record IncrementalPlan(
            boolean incremental,
            boolean skipAnalysis,
            List<String> changedFiles,
            List<String> renamedPaths,
            List<String> rebuildPaths,
            Set<String> replacedPaths,
            List<String> removedPaths,
            String noteOverride,
            String fallbackReason
    ) {
        public static IncrementalPlan full(
                List<String> changedFiles,
                List<String> renamedPaths,
                String noteOverride,
                String fallbackReason
        ) {
            return new IncrementalPlan(false, false, changedFiles, renamedPaths, List.of(), Set.of(), List.of(), noteOverride, fallbackReason);
        }

        public static IncrementalPlan incremental(
                List<String> changedFiles,
                List<String> renamedPaths,
                List<String> rebuildPaths,
                Set<String> replacedPaths,
                List<String> removedPaths,
                String noteOverride
        ) {
            return new IncrementalPlan(
                    true,
                    rebuildPaths.isEmpty(),
                    changedFiles,
                    renamedPaths,
                    rebuildPaths,
                    replacedPaths,
                    removedPaths,
                    noteOverride,
                    null
            );
        }
    }

    public record ResolvedChangedFiles(
            List<String> paths,
            List<String> renamedPaths,
            String notePrefix,
            boolean includesWorkspaceChanges
    ) {
    }
}
