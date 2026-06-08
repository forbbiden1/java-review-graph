package com.acme.graphreview.application;

import com.acme.analyzer.parser.AnalysisRequest;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.GitChangedFiles;
import com.acme.graphreview.infrastructure.GitSnapshotMetadata;
import com.acme.graphreview.infrastructure.GitSnapshotMetadataResolver;
import com.acme.graphreview.infrastructure.JdtProjectAnalyzer;
import com.acme.graphreview.infrastructure.ProjectDescriptorFactory;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.analysis.SourceFileRecord;
import com.acme.model.analysis.AnalysisSnapshot;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectIndexService {

    private final ProjectService projectService;
    private final SnapshotRepository snapshotRepository;
    private final SourceFileRepository sourceFileRepository;
    private final SymbolRepository symbolRepository;
    private final RelationRepository relationRepository;
    private final SymbolChangeRepository symbolChangeRepository;
    private final ProjectDescriptorFactory projectDescriptorFactory;
    private final JdtProjectAnalyzer projectAnalyzer;
    private final GitSnapshotMetadataResolver gitSnapshotMetadataResolver;

    public ProjectIndexService(
            ProjectService projectService,
            SnapshotRepository snapshotRepository,
            SourceFileRepository sourceFileRepository,
            SymbolRepository symbolRepository,
            RelationRepository relationRepository,
            SymbolChangeRepository symbolChangeRepository,
            ProjectDescriptorFactory projectDescriptorFactory,
            JdtProjectAnalyzer projectAnalyzer,
            GitSnapshotMetadataResolver gitSnapshotMetadataResolver
    ) {
        this.projectService = projectService;
        this.snapshotRepository = snapshotRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.symbolRepository = symbolRepository;
        this.relationRepository = relationRepository;
        this.symbolChangeRepository = symbolChangeRepository;
        this.projectDescriptorFactory = projectDescriptorFactory;
        this.projectAnalyzer = projectAnalyzer;
        this.gitSnapshotMetadataResolver = gitSnapshotMetadataResolver;
    }

    public ProjectIndexResult indexProject(String projectId, ProjectIndexCommand command) {
        RegisteredProject project = projectService.getProject(projectId);
        boolean incremental = parseIncrementalFlag(command.mode());
        IncrementalChangeSource incrementalChangeSource = parseIncrementalChangeSource(command.changeSource(), incremental);
        List<String> requestedChangedFiles = sanitizeChangedFiles(command.changedFiles());

        ProjectDescriptor descriptor = projectDescriptorFactory.create(project);
        ProjectSnapshot previousSnapshot = snapshotRepository.findLatestByProjectId(project.id()).orElse(null);
        String previousSnapshotId = previousSnapshot == null ? null : previousSnapshot.id();
        String snapshotId = UUID.randomUUID().toString();
        GitSnapshotMetadata gitMetadata = gitSnapshotMetadataResolver.resolve(descriptor.rootPath());
        ResolvedChangedFiles resolvedChangedFiles = resolveChangedFiles(
                descriptor.rootPath(),
                incremental,
                incrementalChangeSource,
                requestedChangedFiles,
                previousSnapshot
        );
        List<StoredSourceFile> previousFiles = previousSnapshotId == null
                ? List.of()
                : sourceFileRepository.findByProjectIdAndSnapshotId(project.id(), previousSnapshotId);
        List<SymbolRecord> previousSymbols = previousSnapshotId == null
                ? List.of()
                : symbolRepository.findByProjectIdAndSnapshotId(project.id(), previousSnapshotId);
        List<RelationRecord> previousRelations = previousSnapshotId == null
                ? List.of()
                : relationRepository.findByProjectIdAndSnapshotId(project.id(), previousSnapshotId);
        IncrementalPlan incrementalPlan = buildIncrementalPlan(
                descriptor,
                incremental,
                resolvedChangedFiles.paths(),
                resolvedChangedFiles.notePrefix(),
                previousSnapshotId,
                previousFiles,
                previousSymbols,
                previousRelations
        );

        AnalysisSnapshot analysisSnapshot = incrementalPlan.skipAnalysis()
                ? new AnalysisSnapshot(snapshotId, project.id(), Instant.now(), List.of(), List.of(), List.of(), "")
                : projectAnalyzer.analyze(
                        descriptor,
                        new AnalysisRequest(snapshotId, incrementalPlan.incremental(), incrementalPlan.rebuildPaths())
                );
        AnalysisSnapshot assembledAnalysisSnapshot = incrementalPlan.incremental()
                ? mergeIncrementalSnapshot(
                        snapshotId,
                        project.id(),
                        analysisSnapshot,
                        previousFiles,
                        previousSymbols,
                        previousRelations,
                        incrementalPlan
                )
                : withNote(analysisSnapshot, incrementalPlan.noteOverride());

        ProjectSnapshot snapshot = new ProjectSnapshot(
                snapshotId,
                project.id(),
                previousSnapshotId,
                "manual",
                gitMetadata.gitCommit(),
                gitMetadata.gitCommitMessage(),
                snapshotId,
                "completed",
                Instant.now()
        );

        ProjectSnapshot savedSnapshot = snapshotRepository.save(snapshot);
        List<SymbolRecord> currentSymbols = applyChangeStatus(
                assembledAnalysisSnapshot.symbols(),
                previousSymbols,
                assembledAnalysisSnapshot.relations(),
                previousRelations,
                incrementalPlan.incremental()
        );
        AnalysisSnapshot persistedAnalysisSnapshot = new AnalysisSnapshot(
                assembledAnalysisSnapshot.snapshotId(),
                assembledAnalysisSnapshot.projectId(),
                assembledAnalysisSnapshot.createdAt(),
                assembledAnalysisSnapshot.files(),
                currentSymbols,
                assembledAnalysisSnapshot.relations(),
                assembledAnalysisSnapshot.note()
        );

        Map<String, String> fileIdsByPath = buildFileIdByPath(
                sourceFileRepository.saveAll(project.id(), snapshotId, persistedAnalysisSnapshot.files())
        );
        symbolRepository.saveAll(project.id(), snapshotId, persistedAnalysisSnapshot.symbols(), fileIdsByPath);
        relationRepository.saveAll(project.id(), snapshotId, persistedAnalysisSnapshot.relations(), fileIdsByPath);
        symbolChangeRepository.saveAll(buildSymbolChanges(project.id(), snapshotId, previousSymbols, currentSymbols));

        return new ProjectIndexResult(project, savedSnapshot, persistedAnalysisSnapshot);
    }

    public List<ProjectSnapshot> listSnapshots(String projectId) {
        projectService.getProject(projectId);
        return snapshotRepository.findByProjectId(projectId);
    }

    @Transactional
    public ProjectSnapshot renameSnapshot(String projectId, String snapshotId, String displayName) {
        projectService.getProject(projectId);
        snapshotRepository.findByProjectIdAndSnapshotId(projectId, snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));

        String normalizedDisplayName = normalizeDisplayName(displayName);
        return snapshotRepository.rename(projectId, snapshotId, normalizedDisplayName);
    }

    @Transactional
    public void deleteSnapshot(String projectId, String snapshotId) {
        projectService.getProject(projectId);
        snapshotRepository.findByProjectIdAndSnapshotId(projectId, snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
        snapshotRepository.deleteByProjectIdAndSnapshotId(projectId, snapshotId);
    }

    private boolean parseIncrementalFlag(String mode) {
        String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedMode) {
            case "full" -> false;
            case "incremental" -> true;
            default -> throw new ProjectValidationException("Unsupported index mode: " + mode);
        };
    }

    private IncrementalChangeSource parseIncrementalChangeSource(String rawChangeSource, boolean incremental) {
        if (!incremental) {
            return IncrementalChangeSource.MANUAL;
        }

        String normalizedChangeSource = rawChangeSource == null || rawChangeSource.isBlank()
                ? "git"
                : rawChangeSource.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedChangeSource) {
            case "git" -> IncrementalChangeSource.GIT;
            case "manual" -> IncrementalChangeSource.MANUAL;
            default -> throw new ProjectValidationException("Unsupported incremental change source: " + rawChangeSource);
        };
    }

    private List<String> sanitizeChangedFiles(List<String> changedFiles) {
        if (changedFiles == null) {
            return List.of();
        }
        return changedFiles.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::trim)
                .toList();
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new ProjectValidationException("Snapshot display name must not be blank.");
        }
        return displayName.trim();
    }

    private ResolvedChangedFiles resolveChangedFiles(
            Path rootPath,
            boolean incremental,
            IncrementalChangeSource incrementalChangeSource,
            List<String> requestedChangedFiles,
            ProjectSnapshot previousSnapshot
    ) {
        if (!incremental) {
            return new ResolvedChangedFiles(List.of(), null);
        }

        if (incrementalChangeSource == IncrementalChangeSource.MANUAL) {
            if (requestedChangedFiles.isEmpty()) {
                throw new ProjectValidationException("Manual incremental index mode requires at least one changed file.");
            }
            return new ResolvedChangedFiles(requestedChangedFiles, null);
        }

        GitChangedFiles gitChangedFiles = gitSnapshotMetadataResolver.resolveChangedFiles(
                rootPath,
                previousSnapshot == null ? null : previousSnapshot.gitCommit()
        );
        if (!gitChangedFiles.available()) {
            throw new ProjectValidationException(gitChangedFiles.note());
        }
        return new ResolvedChangedFiles(gitChangedFiles.paths(), gitChangedFiles.note());
    }

    private Map<String, String> buildFileIdByPath(List<StoredSourceFile> storedFiles) {
        Map<String, String> fileIdsByPath = new HashMap<>();
        for (StoredSourceFile storedFile : storedFiles) {
            fileIdsByPath.put(storedFile.path(), storedFile.id());
        }
        return fileIdsByPath;
    }

    private IncrementalPlan buildIncrementalPlan(
            ProjectDescriptor descriptor,
            boolean incrementalRequested,
            List<String> changedFiles,
            String changedFilesNotePrefix,
            String previousSnapshotId,
            List<StoredSourceFile> previousFiles,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> previousRelations
    ) {
        if (!incrementalRequested) {
            return IncrementalPlan.full(null);
        }
        if (previousSnapshotId == null) {
            return IncrementalPlan.full(withIncrementalNotePrefix(
                    changedFilesNotePrefix,
                    "Incremental fallback: no previous snapshot was available, so a full scan was executed."
            ));
        }

        LinkedHashSet<String> normalizedChangedPaths = new LinkedHashSet<>();
        boolean buildMetadataChanged = false;
        for (String changedFile : changedFiles) {
            String normalizedPath = normalizeChangedPath(descriptor.rootPath(), changedFile);
            if (normalizedPath == null) {
                continue;
            }
            normalizedChangedPaths.add(normalizedPath);
            if (isBuildMetadataPath(normalizedPath)) {
                buildMetadataChanged = true;
            }
        }

        if (buildMetadataChanged) {
            return IncrementalPlan.full(withIncrementalNotePrefix(
                    changedFilesNotePrefix,
                    "Incremental fallback: build configuration changed, so a full scan was executed."
            ));
        }

        LinkedHashSet<String> changedJavaPaths = normalizedChangedPaths.stream()
                .filter(this::isJavaSourcePath)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (changedJavaPaths.isEmpty()) {
            return IncrementalPlan.incremental(
                    List.of(),
                    Set.of(),
                    withIncrementalNotePrefix(
                            changedFilesNotePrefix,
                            "Incremental request contained no Java source changes; reused previous snapshot data."
                    )
            );
        }

        Map<String, StoredSourceFile> previousFilesByPath = previousFiles.stream()
                .collect(java.util.stream.Collectors.toMap(StoredSourceFile::path, file -> file, (left, right) -> left));
        LinkedHashSet<String> seedPaths = new LinkedHashSet<>(changedJavaPaths);
        Map<String, List<SymbolRecord>> previousSymbolsByFilePath = previousSymbols.stream()
                .collect(java.util.stream.Collectors.groupingBy(SymbolRecord::filePath));
        Set<String> seedSymbolKeys = seedPaths.stream()
                .map(previousSymbolsByFilePath::get)
                .filter(java.util.Objects::nonNull)
                .flatMap(Collection::stream)
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Map<String, SymbolRecord> previousSymbolByKey = previousSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
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
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> replacedPaths = expandedPaths.stream()
                .filter(path -> previousFilesByPath.containsKey(path) || rebuildPaths.contains(path))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        String note = withIncrementalNotePrefix(
                changedFilesNotePrefix,
                buildIncrementalPlanNote(rebuildPaths, replacedPaths)
        );
        return IncrementalPlan.incremental(List.copyOf(rebuildPaths), Set.copyOf(replacedPaths), note);
    }

    private AnalysisSnapshot mergeIncrementalSnapshot(
            String snapshotId,
            String projectId,
            AnalysisSnapshot partialSnapshot,
            List<StoredSourceFile> previousFiles,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> previousRelations,
            IncrementalPlan incrementalPlan
    ) {
        Set<String> replacedPaths = incrementalPlan.replacedPaths();
        Map<String, SymbolRecord> previousSymbolByKey = previousSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));

        List<SourceFileRecord> mergedFiles = new ArrayList<>();
        previousFiles.stream()
                .filter(file -> !replacedPaths.contains(file.path()))
                .map(this::toSourceFileRecord)
                .forEach(mergedFiles::add);
        mergedFiles.addAll(partialSnapshot.files());

        List<SymbolRecord> mergedSymbols = new ArrayList<>();
        previousSymbols.stream()
                .filter(symbol -> !replacedPaths.contains(symbol.filePath()))
                .forEach(mergedSymbols::add);
        mergedSymbols.addAll(partialSnapshot.symbols());

        Map<String, String> mergedTypeKeyByQualifiedName = mergedSymbols.stream()
                .filter(symbol -> symbol.symbolType() == SymbolType.TYPE)
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::qualifiedName, SymbolRecord::symbolKey, (left, right) -> right));
        Set<String> mergedSymbolKeys = mergedSymbols.stream()
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        List<RelationRecord> mergedRelations = new ArrayList<>();
        for (RelationRecord previousRelation : previousRelations) {
            String sourceFilePath = relationSourcePath(previousRelation, previousSymbolByKey);
            if (sourceFilePath != null && replacedPaths.contains(sourceFilePath)) {
                continue;
            }
            if (!relationReferencesExistingSymbols(previousRelation, mergedSymbolKeys)) {
                continue;
            }
            mergedRelations.add(previousRelation);
        }
        partialSnapshot.relations().stream()
                .map(relation -> resolveIncrementalRelation(relation, mergedTypeKeyByQualifiedName))
                .filter(relation -> relationReferencesExistingSymbols(relation, mergedSymbolKeys))
                .forEach(mergedRelations::add);

        return new AnalysisSnapshot(
                snapshotId,
                projectId,
                partialSnapshot.createdAt(),
                deduplicateFiles(mergedFiles),
                deduplicateSymbols(mergedSymbols),
                deduplicateRelations(mergedRelations),
                incrementalPlan.noteOverride()
        );
    }

    private AnalysisSnapshot withNote(AnalysisSnapshot analysisSnapshot, String noteOverride) {
        if (noteOverride == null || noteOverride.isBlank()) {
            return analysisSnapshot;
        }
        return new AnalysisSnapshot(
                analysisSnapshot.snapshotId(),
                analysisSnapshot.projectId(),
                analysisSnapshot.createdAt(),
                analysisSnapshot.files(),
                analysisSnapshot.symbols(),
                analysisSnapshot.relations(),
                noteOverride
        );
    }

    private SourceFileRecord toSourceFileRecord(StoredSourceFile storedSourceFile) {
        return new SourceFileRecord(
                storedSourceFile.path(),
                storedSourceFile.moduleName(),
                storedSourceFile.packageName(),
                storedSourceFile.contentHash(),
                storedSourceFile.scope()
        );
    }

    private List<SourceFileRecord> deduplicateFiles(List<SourceFileRecord> files) {
        Map<String, SourceFileRecord> fileByPath = new LinkedHashMap<>();
        for (SourceFileRecord file : files) {
            fileByPath.put(file.path(), file);
        }
        return List.copyOf(fileByPath.values());
    }

    private List<SymbolRecord> deduplicateSymbols(List<SymbolRecord> symbols) {
        Map<String, SymbolRecord> symbolByKey = new LinkedHashMap<>();
        for (SymbolRecord symbol : symbols) {
            symbolByKey.put(symbol.symbolKey(), symbol);
        }
        return List.copyOf(symbolByKey.values());
    }

    private List<RelationRecord> deduplicateRelations(List<RelationRecord> relations) {
        Map<String, RelationRecord> uniqueRelations = new LinkedHashMap<>();
        for (RelationRecord relation : relations) {
            String key = relation.sourceSymbolKey() + "|" + relation.targetSymbolKey() + "|" + relation.relationType().name();
            uniqueRelations.putIfAbsent(key, relation);
        }
        return List.copyOf(uniqueRelations.values());
    }

    private RelationRecord resolveIncrementalRelation(RelationRecord relation, Map<String, String> mergedTypeKeyByQualifiedName) {
        if (!relation.targetSymbolKey().startsWith("external:type:")) {
            return relation;
        }

        String qualifiedName = relation.targetSymbolKey().substring("external:type:".length());
        String resolvedTarget = mergedTypeKeyByQualifiedName.get(qualifiedName);
        if (resolvedTarget == null) {
            return relation;
        }
        return new RelationRecord(
                relation.sourceSymbolKey(),
                resolvedTarget,
                relation.relationType(),
                relation.confidence(),
                relation.filePath(),
                relation.sourceLine()
        );
    }

    private boolean relationReferencesExistingSymbols(RelationRecord relation, Set<String> mergedSymbolKeys) {
        if (!mergedSymbolKeys.contains(relation.sourceSymbolKey())) {
            return false;
        }
        return relation.targetSymbolKey().startsWith("external:") || mergedSymbolKeys.contains(relation.targetSymbolKey());
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

    private String withIncrementalNotePrefix(String notePrefix, String detail) {
        if (notePrefix == null || notePrefix.isBlank()) {
            return detail;
        }
        if (detail == null || detail.isBlank()) {
            return notePrefix;
        }
        return notePrefix + " " + detail;
    }

    private List<SymbolRecord> applyChangeStatus(
            List<SymbolRecord> currentSymbols,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> currentRelations,
            List<RelationRecord> previousRelations,
            boolean incremental
    ) {
        Map<String, SymbolRecord> previousBySymbolKey = previousSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Map<String, SymbolRecord> currentBySymbolKey = currentSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Set<String> directlyChangedSymbolKeys = new HashSet<>();
        List<SymbolRecord> symbolsWithDirectStatus = new ArrayList<>(currentSymbols.size());

        for (SymbolRecord symbol : currentSymbols) {
            SymbolRecord previous = previousBySymbolKey.get(symbol.symbolKey());
            ChangeStatus changeStatus;
            if (previous == null) {
                changeStatus = ChangeStatus.ADDED;
            } else if (!previous.apiHash().equals(symbol.apiHash())) {
                changeStatus = ChangeStatus.MODIFIED_API;
            } else if (!previous.implHash().equals(symbol.implHash())) {
                changeStatus = ChangeStatus.MODIFIED_IMPL;
            } else {
                changeStatus = ChangeStatus.UNCHANGED;
            }

            if (changeStatus != ChangeStatus.UNCHANGED) {
                directlyChangedSymbolKeys.add(symbol.symbolKey());
            }
            symbolsWithDirectStatus.add(copyWithStatus(symbol, changeStatus));
        }

        Set<String> deletedSymbolKeys = previousBySymbolKey.keySet().stream()
                .filter(symbolKey -> !currentBySymbolKey.containsKey(symbolKey))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (!incremental || (directlyChangedSymbolKeys.isEmpty() && deletedSymbolKeys.isEmpty())) {
            return List.copyOf(symbolsWithDirectStatus);
        }

        Set<String> changedSymbolKeys = new HashSet<>(directlyChangedSymbolKeys);
        changedSymbolKeys.addAll(deletedSymbolKeys);
        Set<String> impactedSymbolKeys = findImpactedSymbolKeys(
                changedSymbolKeys,
                currentSymbols,
                previousSymbols,
                currentRelations,
                previousRelations
        );

        return symbolsWithDirectStatus.stream()
                .map(symbol -> impactedSymbolKeys.contains(symbol.symbolKey()) && symbol.changeStatus() == ChangeStatus.UNCHANGED
                        ? copyWithStatus(symbol, ChangeStatus.IMPACTED)
                        : symbol)
                .toList();
    }

    private SymbolRecord copyWithStatus(SymbolRecord symbol, ChangeStatus changeStatus) {
        return new SymbolRecord(
                symbol.symbolKey(),
                symbol.symbolType(),
                symbol.kind(),
                symbol.parentSymbolKey(),
                symbol.name(),
                symbol.packageName(),
                symbol.qualifiedName(),
                symbol.displayName(),
                symbol.signature(),
                symbol.filePath(),
                symbol.startLine(),
                symbol.endLine(),
                symbol.apiHash(),
                symbol.implHash(),
                changeStatus
        );
    }

    private List<StoredSymbolChange> buildSymbolChanges(
            String projectId,
            String snapshotId,
            List<SymbolRecord> previousSymbols,
            List<SymbolRecord> currentSymbols
    ) {
        Map<String, SymbolRecord> previousBySymbolKey = previousSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Map<String, SymbolRecord> currentBySymbolKey = currentSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));

        List<StoredSymbolChange> changes = new java.util.ArrayList<>();
        for (SymbolRecord current : currentSymbols) {
            if (current.changeStatus() == ChangeStatus.UNCHANGED) {
                continue;
            }
            SymbolRecord previous = previousBySymbolKey.get(current.symbolKey());
            changes.add(new StoredSymbolChange(
                    UUID.randomUUID().toString(),
                    projectId,
                    snapshotId,
                    current.symbolKey(),
                    previous == null ? null : previous.symbolKey(),
                    current.symbolKey(),
                    current.changeStatus().name().toLowerCase(),
                    switch (current.changeStatus()) {
                        case ADDED -> "New symbol discovered in current snapshot.";
                        case MODIFIED_API -> "API hash changed compared with the previous snapshot.";
                        case MODIFIED_IMPL -> "Implementation hash changed compared with the previous snapshot.";
                        case IMPACTED -> "Symbol is directly related to a changed neighbor in the current snapshot.";
                        default -> "Symbol changed.";
                    }
            ));
        }
        for (SymbolRecord previous : previousSymbols) {
            if (currentBySymbolKey.containsKey(previous.symbolKey())) {
                continue;
            }
            changes.add(new StoredSymbolChange(
                    UUID.randomUUID().toString(),
                    projectId,
                    snapshotId,
                    previous.symbolKey(),
                    previous.symbolKey(),
                    null,
                    ChangeStatus.DELETED.name().toLowerCase(),
                    "Symbol no longer exists in the current snapshot."
            ));
        }
        return changes;
    }

    private Set<String> findImpactedSymbolKeys(
            Set<String> changedSymbolKeys,
            List<SymbolRecord> currentSymbols,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> currentRelations,
            List<RelationRecord> previousRelations
    ) {
        Map<String, SymbolRecord> currentBySymbolKey = currentSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Map<String, SymbolRecord> previousBySymbolKey = previousSymbols.stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));

        Map<String, Set<String>> neighborsBySymbolKey = new HashMap<>();
        currentSymbols.forEach(symbol -> neighborsBySymbolKey.put(symbol.symbolKey(), new HashSet<>()));

        for (RelationRecord relation : currentRelations) {
            if (!isImpactRelation(relation.relationType())) {
                continue;
            }
            if (currentBySymbolKey.containsKey(relation.sourceSymbolKey()) && currentBySymbolKey.containsKey(relation.targetSymbolKey())) {
                neighborsBySymbolKey.computeIfAbsent(relation.sourceSymbolKey(), ignored -> new HashSet<>()).add(relation.targetSymbolKey());
                neighborsBySymbolKey.computeIfAbsent(relation.targetSymbolKey(), ignored -> new HashSet<>()).add(relation.sourceSymbolKey());
            }
        }

        for (RelationRecord relation : previousRelations) {
            if (!isImpactRelation(relation.relationType())) {
                continue;
            }
            boolean sourceDeleted = previousBySymbolKey.containsKey(relation.sourceSymbolKey()) && !currentBySymbolKey.containsKey(relation.sourceSymbolKey());
            boolean targetDeleted = previousBySymbolKey.containsKey(relation.targetSymbolKey()) && !currentBySymbolKey.containsKey(relation.targetSymbolKey());
            if (sourceDeleted && currentBySymbolKey.containsKey(relation.targetSymbolKey())) {
                neighborsBySymbolKey.computeIfAbsent(relation.sourceSymbolKey(), ignored -> new HashSet<>()).add(relation.targetSymbolKey());
                neighborsBySymbolKey.computeIfAbsent(relation.targetSymbolKey(), ignored -> new HashSet<>()).add(relation.sourceSymbolKey());
            }
            if (targetDeleted && currentBySymbolKey.containsKey(relation.sourceSymbolKey())) {
                neighborsBySymbolKey.computeIfAbsent(relation.targetSymbolKey(), ignored -> new HashSet<>()).add(relation.sourceSymbolKey());
                neighborsBySymbolKey.computeIfAbsent(relation.sourceSymbolKey(), ignored -> new HashSet<>()).add(relation.targetSymbolKey());
            }
        }

        Set<String> impactedSymbolKeys = new HashSet<>();
        for (String changedSymbolKey : changedSymbolKeys) {
            SymbolRecord changedSymbol = currentBySymbolKey.get(changedSymbolKey);
            if (changedSymbol == null) {
                changedSymbol = previousBySymbolKey.get(changedSymbolKey);
            }
            if (changedSymbol == null) {
                continue;
            }

            for (String neighborKey : neighborsBySymbolKey.getOrDefault(changedSymbolKey, Set.of())) {
                SymbolRecord neighbor = currentBySymbolKey.get(neighborKey);
                if (neighbor == null) {
                    continue;
                }
                if (neighbor.symbolType() == changedSymbol.symbolType()) {
                    impactedSymbolKeys.add(neighborKey);
                } else if (neighbor.symbolType() == com.acme.model.graph.SymbolType.TYPE) {
                    impactedSymbolKeys.add(neighborKey);
                } else if (changedSymbol.symbolType() == com.acme.model.graph.SymbolType.TYPE) {
                    impactedSymbolKeys.add(neighborKey);
                }
            }

            if (changedSymbol.parentSymbolKey() != null && currentBySymbolKey.containsKey(changedSymbol.parentSymbolKey())) {
                impactedSymbolKeys.add(changedSymbol.parentSymbolKey());
            }
        }

        impactedSymbolKeys.removeAll(changedSymbolKeys);
        return impactedSymbolKeys;
    }

    private boolean isImpactRelation(RelationType relationType) {
        return switch (relationType) {
            case EXTENDS, IMPLEMENTS, USES_TYPE, CALLS, OVERRIDES, DECLARES -> true;
        };
    }

    private record IncrementalPlan(
            boolean incremental,
            boolean skipAnalysis,
            List<String> rebuildPaths,
            Set<String> replacedPaths,
            String noteOverride
    ) {
        private static IncrementalPlan full(String noteOverride) {
            return new IncrementalPlan(false, false, List.of(), Set.of(), noteOverride);
        }

        private static IncrementalPlan incremental(List<String> rebuildPaths, Set<String> replacedPaths, String noteOverride) {
            return new IncrementalPlan(true, rebuildPaths.isEmpty(), rebuildPaths, replacedPaths, noteOverride);
        }
    }

    private record ResolvedChangedFiles(
            List<String> paths,
            String notePrefix
    ) {
    }

    private enum IncrementalChangeSource {
        GIT,
        MANUAL
    }
}
