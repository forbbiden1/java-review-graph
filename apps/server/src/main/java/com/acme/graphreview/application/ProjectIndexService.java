package com.acme.graphreview.application;

import com.acme.analyzer.parser.AnalysisRequest;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.graphreview.infrastructure.GitSnapshotMetadata;
import com.acme.graphreview.infrastructure.GitSnapshotMetadataResolver;
import com.acme.graphreview.infrastructure.JdtProjectAnalyzer;
import com.acme.graphreview.infrastructure.ProjectDescriptorFactory;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.analysis.AnalysisSnapshot;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.SymbolRecord;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final IncrementalPlanner incrementalPlanner;
    private final SnapshotAssembler snapshotAssembler;
    private final ChangeStatusCalculator changeStatusCalculator;

    @Autowired
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
        this(
                projectService,
                snapshotRepository,
                sourceFileRepository,
                symbolRepository,
                relationRepository,
                symbolChangeRepository,
                projectDescriptorFactory,
                projectAnalyzer,
                gitSnapshotMetadataResolver,
                new IncrementalPlanner(gitSnapshotMetadataResolver),
                new SnapshotAssembler(),
                new ChangeStatusCalculator()
        );
    }

    public ProjectIndexService(
            ProjectService projectService,
            SnapshotRepository snapshotRepository,
            SourceFileRepository sourceFileRepository,
            SymbolRepository symbolRepository,
            RelationRepository relationRepository,
            SymbolChangeRepository symbolChangeRepository,
            ProjectDescriptorFactory projectDescriptorFactory,
            JdtProjectAnalyzer projectAnalyzer,
            GitSnapshotMetadataResolver gitSnapshotMetadataResolver,
            IncrementalPlanner incrementalPlanner,
            SnapshotAssembler snapshotAssembler,
            ChangeStatusCalculator changeStatusCalculator
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
        this.incrementalPlanner = incrementalPlanner;
        this.snapshotAssembler = snapshotAssembler;
        this.changeStatusCalculator = changeStatusCalculator;
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
        IncrementalPlanner.ResolvedChangedFiles resolvedChangedFiles = incrementalPlanner.resolveChangedFiles(
                descriptor.rootPath(),
                incremental,
                toPlannerChangeSource(incrementalChangeSource),
                requestedChangedFiles,
                previousSnapshot
        );
        GitSnapshotMetadata gitMetadata = gitSnapshotMetadataResolver.resolve(descriptor.rootPath());
        GitSnapshotMetadata snapshotGitMetadata = incrementalPlanner.shouldStoreSnapshotAsUncommitted(
                incremental,
                toPlannerChangeSource(incrementalChangeSource),
                resolvedChangedFiles.includesWorkspaceChanges()
        ) ? GitSnapshotMetadata.uncommitted() : gitMetadata;
        String requestedMode = incremental ? "incremental" : "full";
        String changeSource = incremental ? incrementalChangeSource.name().toLowerCase(Locale.ROOT) : null;
        List<StoredSourceFile> previousFiles = previousSnapshotId == null
                ? List.of()
                : sourceFileRepository.findByProjectIdAndSnapshotId(project.id(), previousSnapshotId);
        List<SymbolRecord> previousSymbols = previousSnapshotId == null
                ? List.of()
                : symbolRepository.findByProjectIdAndSnapshotId(project.id(), previousSnapshotId);
        List<RelationRecord> previousRelations = previousSnapshotId == null
                ? List.of()
                : relationRepository.findByProjectIdAndSnapshotId(project.id(), previousSnapshotId);
        IncrementalPlanner.IncrementalPlan incrementalPlan = incrementalPlanner.buildIncrementalPlan(
                descriptor,
                incremental,
                resolvedChangedFiles.paths(),
                resolvedChangedFiles.renamedPaths(),
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
                ? snapshotAssembler.mergeIncrementalSnapshot(
                        snapshotId,
                        project.id(),
                        analysisSnapshot,
                        previousFiles,
                        previousSymbols,
                        previousRelations,
                        incrementalPlan
                )
                : snapshotAssembler.withNote(analysisSnapshot, incrementalPlan.noteOverride());

        ProjectSnapshot snapshot = new ProjectSnapshot(
                snapshotId,
                project.id(),
                previousSnapshotId,
                incremental ? changeSource : "manual",
                snapshotGitMetadata.gitCommit(),
                snapshotGitMetadata.gitCommitMessage(),
                snapshotId,
                "completed",
                Instant.now(),
                requestedMode,
                incrementalPlan.incremental() ? "incremental" : "full",
                changeSource,
                resolvedChangedFiles.includesWorkspaceChanges(),
                buildDiagnosticsNote(incremental, incrementalPlan),
                incrementalPlan.fallbackReason(),
                incrementalPlan.changedFiles(),
                resolvedChangedFiles.renamedPaths(),
                incrementalPlan.rebuildPaths(),
                incrementalPlan.removedPaths()
        );

        ProjectSnapshot savedSnapshot = snapshotRepository.save(snapshot);
        List<SymbolRecord> currentSymbols = changeStatusCalculator.applyChangeStatus(
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

        var fileIdsByPath = snapshotAssembler.buildFileIdByPath(
                sourceFileRepository.saveAll(project.id(), snapshotId, persistedAnalysisSnapshot.files())
        );
        symbolRepository.saveAll(project.id(), snapshotId, persistedAnalysisSnapshot.symbols(), fileIdsByPath);
        relationRepository.saveAll(project.id(), snapshotId, persistedAnalysisSnapshot.relations(), fileIdsByPath);
        symbolChangeRepository.saveAll(
                changeStatusCalculator.buildSymbolChanges(project.id(), snapshotId, previousSymbols, currentSymbols)
        );

        return new ProjectIndexResult(project, savedSnapshot, persistedAnalysisSnapshot);
    }

    public List<ProjectSnapshot> listSnapshots(String projectId) {
        projectService.getProject(projectId);
        return snapshotRepository.findByProjectId(projectId);
    }

    public ProjectSnapshot getSnapshotDiagnostics(String projectId, String snapshotId) {
        projectService.getProject(projectId);
        return snapshotRepository.findByProjectIdAndSnapshotId(projectId, snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
    }

    @Transactional
    public ProjectSnapshot renameSnapshot(String projectId, String snapshotId, String displayName) {
        projectService.getProject(projectId);
        snapshotRepository.findByProjectIdAndSnapshotId(projectId, snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));

        String normalizedDisplayName = incrementalPlanner.normalizeDisplayName(displayName);
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

    private String buildDiagnosticsNote(boolean incrementalRequested, IncrementalPlanner.IncrementalPlan incrementalPlan) {
        String note = incrementalRequested
                ? incrementalPlan.noteOverride()
                : "Full index requested; scanned all discovered Java source files.";
        return incrementalPlanner.withIncrementalNotePrefix(note, buildRenameSummary(incrementalPlan.renamedPaths()));
    }

    private String buildRenameSummary(List<String> renamedPaths) {
        if (renamedPaths == null || renamedPaths.isEmpty()) {
            return null;
        }
        if (renamedPaths.size() == 1) {
            return "Detected rename/move: " + renamedPaths.get(0) + ".";
        }
        return "Detected " + renamedPaths.size() + " rename/move path(s).";
    }

    private enum IncrementalChangeSource {
        GIT,
        MANUAL
    }

    private IncrementalPlanner.IncrementalChangeSource toPlannerChangeSource(IncrementalChangeSource source) {
        return source == IncrementalChangeSource.GIT
                ? IncrementalPlanner.IncrementalChangeSource.GIT
                : IncrementalPlanner.IncrementalChangeSource.MANUAL;
    }
}
