package com.acme.graphreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.acme.model.analysis.AnalysisSnapshot;
import com.acme.model.analysis.SourceFileRecord;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectIndexServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void marksOneHopNeighborsAsImpactedDuringIncrementalIndex() throws Exception {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "demo"));
        java.nio.file.Files.createDirectories(sourceRoot);
        java.nio.file.Files.writeString(sourceRoot.resolve("Service.java"), "package demo; class Service {}");
        java.nio.file.Files.writeString(sourceRoot.resolve("Controller.java"), "package demo; class Controller {}");

        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                tempDir.toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot previousSnapshot = new ProjectSnapshot(
                "snapshot-0",
                project.id(),
                null,
                "manual",
                null,
                null,
                "snapshot-0",
                "completed",
                Instant.now()
        );

        SymbolRecord previousService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v1");
        SymbolRecord previousController = typeSymbol(
                "type:root:demo.Controller",
                "demo.Controller",
                "Controller",
                "api-controller-v1",
                "impl-controller-v1"
        );
        RelationRecord previousUses = relation(previousController.symbolKey(), previousService.symbolKey(), RelationType.USES_TYPE);

        SymbolRecord currentService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v2");
        SymbolRecord currentController = typeSymbol(
                "type:root:demo.Controller",
                "demo.Controller",
                "Controller",
                "api-controller-v1",
                "impl-controller-v1"
        );
        RelationRecord currentUses = relation(currentController.symbolKey(), currentService.symbolKey(), RelationType.USES_TYPE);

        AnalysisSnapshot incrementalSnapshot = new AnalysisSnapshot(
                "snapshot-1",
                project.id(),
                Instant.now(),
                List.of(file("src/main/java/demo/Service.java"), file("src/main/java/demo/Controller.java")),
                List.of(currentService, currentController),
                List.of(currentUses),
                "incremental"
        );

        InMemorySnapshotRepository snapshotRepository = new InMemorySnapshotRepository(previousSnapshot);
        InMemorySymbolRepository symbolRepository = new InMemorySymbolRepository(Map.of(previousSnapshot.id(), List.of(previousService, previousController)));
        InMemoryRelationRepository relationRepository = new InMemoryRelationRepository(Map.of(previousSnapshot.id(), List.of(previousUses)));
        InMemorySymbolChangeRepository symbolChangeRepository = new InMemorySymbolChangeRepository();
        InMemorySourceFileRepository sourceFileRepository = new InMemorySourceFileRepository(Map.of(
                previousSnapshot.id(),
                List.of(
                        new StoredSourceFile("prev-service-file", "src/main/java/demo/Service.java", "root", "demo", "content-hash-service", "main"),
                        new StoredSourceFile("prev-controller-file", "src/main/java/demo/Controller.java", "root", "demo", "content-hash-controller", "main")
                )
        ));

        StubJdtProjectAnalyzer analyzer = new StubJdtProjectAnalyzer(incrementalSnapshot);
        ProjectIndexService service = new ProjectIndexService(
                new StubProjectService(project),
                snapshotRepository,
                sourceFileRepository,
                symbolRepository,
                relationRepository,
                symbolChangeRepository,
                new StubProjectDescriptorFactory(project),
                analyzer,
                new StubGitSnapshotMetadataResolver()
        );

        ProjectIndexResult result = service.indexProject(
                project.id(),
                new ProjectIndexCommand("incremental", "manual", List.of("src/main/java/demo/Service.java"))
        );

        assertNotNull(analyzer.lastRequest);

        List<SymbolRecord> storedCurrentSymbols = symbolRepository.findByProjectIdAndSnapshotId(project.id(), result.snapshot().id());
        Map<String, ChangeStatus> statusBySymbolKey = new HashMap<>();
        storedCurrentSymbols.forEach(symbol -> statusBySymbolKey.put(symbol.symbolKey(), symbol.changeStatus()));

        assertEquals(
                Set.of("src/main/java/demo/Service.java", "src/main/java/demo/Controller.java"),
                Set.copyOf(analyzer.lastRequest.changedFiles())
        );
        assertEquals(ChangeStatus.MODIFIED_IMPL, statusBySymbolKey.get(currentService.symbolKey()));
        assertEquals(ChangeStatus.IMPACTED, statusBySymbolKey.get(currentController.symbolKey()));

        assertTrue(
                symbolChangeRepository.savedChanges.stream().anyMatch(change ->
                        change.symbolKey().equals(currentController.symbolKey()) && change.changeType().equals("impacted"))
        );
    }

    @Test
    void reusesPreviousSnapshotWhenIncrementalRequestHasNoJavaChanges() throws Exception {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "demo"));
        java.nio.file.Files.createDirectories(sourceRoot);
        java.nio.file.Files.writeString(sourceRoot.resolve("Service.java"), "package demo; class Service {}");
        java.nio.file.Files.writeString(tempDir.resolve("README.md"), "# demo");

        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                tempDir.toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot previousSnapshot = new ProjectSnapshot(
                "snapshot-0",
                project.id(),
                null,
                "manual",
                null,
                null,
                "snapshot-0",
                "completed",
                Instant.now()
        );

        SymbolRecord previousService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v1");

        InMemorySnapshotRepository snapshotRepository = new InMemorySnapshotRepository(previousSnapshot);
        InMemorySymbolRepository symbolRepository = new InMemorySymbolRepository(Map.of(previousSnapshot.id(), List.of(previousService)));
        InMemoryRelationRepository relationRepository = new InMemoryRelationRepository(Map.of(previousSnapshot.id(), List.of()));
        InMemorySymbolChangeRepository symbolChangeRepository = new InMemorySymbolChangeRepository();
        InMemorySourceFileRepository sourceFileRepository = new InMemorySourceFileRepository(Map.of(
                previousSnapshot.id(),
                List.of(new StoredSourceFile(
                        "prev-service-file",
                        "src/main/java/demo/Service.java",
                        "root",
                        "demo",
                        "content-hash-service",
                        "main"
                ))
        ));
        StubJdtProjectAnalyzer analyzer = new StubJdtProjectAnalyzer(new AnalysisSnapshot(
                "snapshot-1",
                project.id(),
                Instant.now(),
                List.of(),
                List.of(),
                List.of(),
                "unused"
        ));

        ProjectIndexService service = new ProjectIndexService(
                new StubProjectService(project),
                snapshotRepository,
                sourceFileRepository,
                symbolRepository,
                relationRepository,
                symbolChangeRepository,
                new StubProjectDescriptorFactory(project),
                analyzer,
                new StubGitSnapshotMetadataResolver()
        );

        ProjectIndexResult result = service.indexProject(
                project.id(),
                new ProjectIndexCommand("incremental", "manual", List.of("README.md"))
        );

        assertEquals(0, analyzer.callCount);
        assertEquals(
                "Incremental request contained no Java source changes; reused previous snapshot data.",
                result.analysisSnapshot().note()
        );
        assertTrue(symbolChangeRepository.savedChanges.isEmpty());

        List<SymbolRecord> storedCurrentSymbols = symbolRepository.findByProjectIdAndSnapshotId(project.id(), result.snapshot().id());
        assertEquals(1, storedCurrentSymbols.size());
        assertEquals(ChangeStatus.UNCHANGED, storedCurrentSymbols.get(0).changeStatus());

        List<StoredSourceFile> storedFiles = sourceFileRepository.findByProjectIdAndSnapshotId(project.id(), result.snapshot().id());
        assertEquals(1, storedFiles.size());
        assertEquals("src/main/java/demo/Service.java", storedFiles.get(0).path());
    }

    @Test
    void fallsBackToFullScanWhenBuildMetadataChanges() throws Exception {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "demo"));
        java.nio.file.Files.createDirectories(sourceRoot);
        java.nio.file.Files.writeString(sourceRoot.resolve("Service.java"), "package demo; class Service {}");

        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                tempDir.toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot previousSnapshot = new ProjectSnapshot(
                "snapshot-0",
                project.id(),
                null,
                "manual",
                null,
                null,
                "snapshot-0",
                "completed",
                Instant.now()
        );

        SymbolRecord previousService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v1");
        AnalysisSnapshot fullSnapshot = new AnalysisSnapshot(
                "snapshot-1",
                project.id(),
                Instant.now(),
                List.of(file("src/main/java/demo/Service.java")),
                List.of(previousService),
                List.of(),
                "full"
        );

        InMemorySnapshotRepository snapshotRepository = new InMemorySnapshotRepository(previousSnapshot);
        InMemorySymbolRepository symbolRepository = new InMemorySymbolRepository(Map.of(previousSnapshot.id(), List.of(previousService)));
        InMemoryRelationRepository relationRepository = new InMemoryRelationRepository(Map.of(previousSnapshot.id(), List.of()));
        InMemorySymbolChangeRepository symbolChangeRepository = new InMemorySymbolChangeRepository();
        InMemorySourceFileRepository sourceFileRepository = new InMemorySourceFileRepository(Map.of(
                previousSnapshot.id(),
                List.of(new StoredSourceFile(
                        "prev-service-file",
                        "src/main/java/demo/Service.java",
                        "root",
                        "demo",
                        "content-hash-service",
                        "main"
                ))
        ));
        StubJdtProjectAnalyzer analyzer = new StubJdtProjectAnalyzer(fullSnapshot);

        ProjectIndexService service = new ProjectIndexService(
                new StubProjectService(project),
                snapshotRepository,
                sourceFileRepository,
                symbolRepository,
                relationRepository,
                symbolChangeRepository,
                new StubProjectDescriptorFactory(project),
                analyzer,
                new StubGitSnapshotMetadataResolver()
        );

        ProjectIndexResult result = service.indexProject(
                project.id(),
                new ProjectIndexCommand("incremental", "manual", List.of("pom.xml"))
        );

        assertEquals(1, analyzer.callCount);
        assertNotNull(analyzer.lastRequest);
        assertFalse(analyzer.lastRequest.incremental());
        assertTrue(analyzer.lastRequest.changedFiles().isEmpty());
        assertEquals(
                "Incremental fallback: build configuration changed, so a full scan was executed.",
                result.analysisSnapshot().note()
        );
    }

    @Test
    void marksDeletedSymbolsAndTheirNeighborsDuringIncrementalIndex() throws Exception {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "demo"));
        java.nio.file.Files.createDirectories(sourceRoot);
        java.nio.file.Files.writeString(sourceRoot.resolve("Controller.java"), "package demo; class Controller {}");

        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                tempDir.toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot previousSnapshot = new ProjectSnapshot(
                "snapshot-0",
                project.id(),
                null,
                "manual",
                null,
                null,
                "snapshot-0",
                "completed",
                Instant.now()
        );

        SymbolRecord previousService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v1");
        SymbolRecord previousController = typeSymbol(
                "type:root:demo.Controller",
                "demo.Controller",
                "Controller",
                "api-controller-v1",
                "impl-controller-v1"
        );
        RelationRecord previousUses = relation(
                previousController.symbolKey(),
                previousService.symbolKey(),
                RelationType.USES_TYPE,
                "src/main/java/demo/Controller.java"
        );

        SymbolRecord currentController = typeSymbol(
                "type:root:demo.Controller",
                "demo.Controller",
                "Controller",
                "api-controller-v1",
                "impl-controller-v1"
        );
        AnalysisSnapshot incrementalSnapshot = new AnalysisSnapshot(
                "snapshot-1",
                project.id(),
                Instant.now(),
                List.of(file("src/main/java/demo/Controller.java")),
                List.of(currentController),
                List.of(),
                "incremental"
        );

        InMemorySnapshotRepository snapshotRepository = new InMemorySnapshotRepository(previousSnapshot);
        InMemorySymbolRepository symbolRepository = new InMemorySymbolRepository(Map.of(previousSnapshot.id(), List.of(previousService, previousController)));
        InMemoryRelationRepository relationRepository = new InMemoryRelationRepository(Map.of(previousSnapshot.id(), List.of(previousUses)));
        InMemorySymbolChangeRepository symbolChangeRepository = new InMemorySymbolChangeRepository();
        InMemorySourceFileRepository sourceFileRepository = new InMemorySourceFileRepository(Map.of(
                previousSnapshot.id(),
                List.of(
                        new StoredSourceFile("prev-service-file", "src/main/java/demo/Service.java", "root", "demo", "content-hash-service", "main"),
                        new StoredSourceFile("prev-controller-file", "src/main/java/demo/Controller.java", "root", "demo", "content-hash-controller", "main")
                )
        ));
        StubJdtProjectAnalyzer analyzer = new StubJdtProjectAnalyzer(incrementalSnapshot);

        ProjectIndexService service = new ProjectIndexService(
                new StubProjectService(project),
                snapshotRepository,
                sourceFileRepository,
                symbolRepository,
                relationRepository,
                symbolChangeRepository,
                new StubProjectDescriptorFactory(project),
                analyzer,
                new StubGitSnapshotMetadataResolver()
        );

        ProjectIndexResult result = service.indexProject(
                project.id(),
                new ProjectIndexCommand("incremental", "manual", List.of("src/main/java/demo/Service.java"))
        );

        assertEquals(1, analyzer.callCount);
        assertNotNull(analyzer.lastRequest);
        assertTrue(analyzer.lastRequest.incremental());
        assertEquals(List.of("src/main/java/demo/Controller.java"), analyzer.lastRequest.changedFiles());
        assertEquals(
                "Incremental snapshot rebuilt 1 Java file(s) and removed 1 file(s).",
                result.analysisSnapshot().note()
        );

        List<SymbolRecord> storedCurrentSymbols = symbolRepository.findByProjectIdAndSnapshotId(project.id(), result.snapshot().id());
        assertEquals(1, storedCurrentSymbols.size());
        assertEquals(currentController.symbolKey(), storedCurrentSymbols.get(0).symbolKey());
        assertEquals(ChangeStatus.IMPACTED, storedCurrentSymbols.get(0).changeStatus());

        List<StoredSourceFile> storedFiles = sourceFileRepository.findByProjectIdAndSnapshotId(project.id(), result.snapshot().id());
        assertEquals(1, storedFiles.size());
        assertEquals("src/main/java/demo/Controller.java", storedFiles.get(0).path());

        assertTrue(
                symbolChangeRepository.savedChanges.stream().anyMatch(change ->
                        change.symbolKey().equals(currentController.symbolKey()) && change.changeType().equals("impacted"))
        );
        assertTrue(
                symbolChangeRepository.savedChanges.stream().anyMatch(change ->
                        change.symbolKey().equals(previousService.symbolKey()) && change.changeType().equals("deleted"))
        );
    }

    @Test
    void resolvesIncrementalChangesFromGitWhenRequested() throws Exception {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "demo"));
        java.nio.file.Files.createDirectories(sourceRoot);
        java.nio.file.Files.writeString(sourceRoot.resolve("Service.java"), "package demo; class Service {}");
        java.nio.file.Files.writeString(sourceRoot.resolve("Controller.java"), "package demo; class Controller {}");

        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                tempDir.toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot previousSnapshot = new ProjectSnapshot(
                "snapshot-0",
                project.id(),
                null,
                "manual",
                "abcdef1234567890",
                "Baseline",
                "snapshot-0",
                "completed",
                Instant.now()
        );

        SymbolRecord previousService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v1");
        SymbolRecord previousController = typeSymbol(
                "type:root:demo.Controller",
                "demo.Controller",
                "Controller",
                "api-controller-v1",
                "impl-controller-v1"
        );
        RelationRecord previousUses = relation(previousController.symbolKey(), previousService.symbolKey(), RelationType.USES_TYPE);

        SymbolRecord currentService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v2");
        SymbolRecord currentController = typeSymbol(
                "type:root:demo.Controller",
                "demo.Controller",
                "Controller",
                "api-controller-v1",
                "impl-controller-v1"
        );
        AnalysisSnapshot incrementalSnapshot = new AnalysisSnapshot(
                "snapshot-1",
                project.id(),
                Instant.now(),
                List.of(file("src/main/java/demo/Service.java"), file("src/main/java/demo/Controller.java")),
                List.of(currentService, currentController),
                List.of(relation(currentController.symbolKey(), currentService.symbolKey(), RelationType.USES_TYPE)),
                "incremental"
        );

        InMemorySnapshotRepository snapshotRepository = new InMemorySnapshotRepository(previousSnapshot);
        InMemorySymbolRepository symbolRepository = new InMemorySymbolRepository(Map.of(previousSnapshot.id(), List.of(previousService, previousController)));
        InMemoryRelationRepository relationRepository = new InMemoryRelationRepository(Map.of(previousSnapshot.id(), List.of(previousUses)));
        InMemorySymbolChangeRepository symbolChangeRepository = new InMemorySymbolChangeRepository();
        InMemorySourceFileRepository sourceFileRepository = new InMemorySourceFileRepository(Map.of(
                previousSnapshot.id(),
                List.of(
                        new StoredSourceFile("prev-service-file", "src/main/java/demo/Service.java", "root", "demo", "content-hash-service", "main"),
                        new StoredSourceFile("prev-controller-file", "src/main/java/demo/Controller.java", "root", "demo", "content-hash-controller", "main")
                )
        ));
        StubJdtProjectAnalyzer analyzer = new StubJdtProjectAnalyzer(incrementalSnapshot);
        StubGitSnapshotMetadataResolver gitResolver = new StubGitSnapshotMetadataResolver();
        gitResolver.changedFiles = GitChangedFiles.available(
                List.of("src/main/java/demo/Service.java"),
                "Incremental Git diff collected 1 changed path(s) from commit abcdef12 to the current workspace state."
        );

        ProjectIndexService service = new ProjectIndexService(
                new StubProjectService(project),
                snapshotRepository,
                sourceFileRepository,
                symbolRepository,
                relationRepository,
                symbolChangeRepository,
                new StubProjectDescriptorFactory(project),
                analyzer,
                gitResolver
        );

        ProjectIndexResult result = service.indexProject(
                project.id(),
                new ProjectIndexCommand("incremental", "git", List.of())
        );

        assertEquals("abcdef1234567890", gitResolver.lastBaseCommit);
        assertNotNull(gitResolver.lastChangedFilesRootPath);
        assertEquals(
                Set.of("src/main/java/demo/Service.java", "src/main/java/demo/Controller.java"),
                Set.copyOf(analyzer.lastRequest.changedFiles())
        );
        assertTrue(result.analysisSnapshot().note().startsWith("Incremental Git diff collected 1 changed path(s)"));
    }

    @Test
    void storesGitIncrementalSnapshotAsUncommittedWhenWorkspaceChangesAreIncluded() throws Exception {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "demo"));
        java.nio.file.Files.createDirectories(sourceRoot);
        java.nio.file.Files.writeString(sourceRoot.resolve("Service.java"), "package demo; class Service {}");

        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                tempDir.toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot previousSnapshot = new ProjectSnapshot(
                "snapshot-0",
                project.id(),
                null,
                "manual",
                "abcdef1234567890",
                "Baseline",
                "snapshot-0",
                "completed",
                Instant.now()
        );

        SymbolRecord currentService = typeSymbol("type:root:demo.Service", "demo.Service", "Service", "api-service-v1", "impl-service-v2");
        AnalysisSnapshot incrementalSnapshot = new AnalysisSnapshot(
                "snapshot-1",
                project.id(),
                Instant.now(),
                List.of(file("src/main/java/demo/Service.java")),
                List.of(currentService),
                List.of(),
                "incremental"
        );

        StubJdtProjectAnalyzer analyzer = new StubJdtProjectAnalyzer(incrementalSnapshot);
        StubGitSnapshotMetadataResolver gitResolver = new StubGitSnapshotMetadataResolver();
        gitResolver.metadata = new GitSnapshotMetadata("fedcba9876543210", "Committed head");
        gitResolver.changedFiles = GitChangedFiles.available(
                List.of("src/main/java/demo/Service.java"),
                "Incremental Git diff collected 1 changed path(s) from the current working tree based on commit abcdef12.",
                true
        );

        ProjectIndexService service = new ProjectIndexService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(previousSnapshot),
                new InMemorySourceFileRepository(Map.of(previousSnapshot.id(), List.of())),
                new InMemorySymbolRepository(Map.of(previousSnapshot.id(), List.of())),
                new InMemoryRelationRepository(Map.of(previousSnapshot.id(), List.of())),
                new InMemorySymbolChangeRepository(),
                new StubProjectDescriptorFactory(project),
                analyzer,
                gitResolver
        );

        ProjectIndexResult result = service.indexProject(
                project.id(),
                new ProjectIndexCommand("incremental", "git", List.of())
        );

        assertNull(result.snapshot().gitCommit());
        assertNull(result.snapshot().gitCommitMessage());
    }

    private static SymbolRecord typeSymbol(String symbolKey, String qualifiedName, String name, String apiHash, String implHash) {
        return new SymbolRecord(
                symbolKey,
                SymbolType.TYPE,
                SymbolKind.CLASS,
                null,
                name,
                "demo",
                qualifiedName,
                name,
                qualifiedName,
                "src/main/java/demo/" + name + ".java",
                1,
                20,
                apiHash,
                implHash,
                ChangeStatus.UNCHANGED
        );
    }

    private static RelationRecord relation(String sourceSymbolKey, String targetSymbolKey, RelationType relationType) {
        return relation(sourceSymbolKey, targetSymbolKey, relationType, "src/main/java/demo/Controller.java");
    }

    private static RelationRecord relation(String sourceSymbolKey, String targetSymbolKey, RelationType relationType, String filePath) {
        return new RelationRecord(sourceSymbolKey, targetSymbolKey, relationType, "exact", filePath, 1);
    }

    private static SourceFileRecord file(String path) {
        return new SourceFileRecord(path, "root", "demo", "content-hash-" + path, "main");
    }

    private static final class StubProjectService extends ProjectService {

        private final RegisteredProject project;

        private StubProjectService(RegisteredProject project) {
            super(new NoOpProjectRepository(), new NoOpBuildToolDetector());
            this.project = project;
        }

        @Override
        public RegisteredProject getProject(String projectId) {
            return project;
        }
    }

    private static final class StubProjectDescriptorFactory extends ProjectDescriptorFactory {

        private final RegisteredProject project;

        private StubProjectDescriptorFactory(RegisteredProject project) {
            this.project = project;
        }

        @Override
        public ProjectDescriptor create(RegisteredProject ignored) {
            Path rootPath = Path.of(project.rootPath());
            return new ProjectDescriptor(project.id(), project.buildTool(), rootPath, List.of(rootPath), List.of(rootPath));
        }
    }

    private static final class StubJdtProjectAnalyzer extends JdtProjectAnalyzer {

        private final AnalysisSnapshot analysisSnapshot;
        private int callCount;
        private AnalysisRequest lastRequest;

        private StubJdtProjectAnalyzer(AnalysisSnapshot analysisSnapshot) {
            this.analysisSnapshot = analysisSnapshot;
        }

        @Override
        public AnalysisSnapshot analyze(ProjectDescriptor descriptor, AnalysisRequest request) {
            callCount += 1;
            lastRequest = request;
            return analysisSnapshot;
        }
    }

    private static final class StubGitSnapshotMetadataResolver extends GitSnapshotMetadataResolver {
        private GitSnapshotMetadata metadata = GitSnapshotMetadata.uncommitted();
        private GitChangedFiles changedFiles = GitChangedFiles.available(List.of(), "git");
        private Path lastChangedFilesRootPath;
        private String lastBaseCommit;

        @Override
        public GitSnapshotMetadata resolve(Path rootPath) {
            return metadata;
        }

        @Override
        public GitChangedFiles resolveChangedFiles(Path rootPath, String baseCommit) {
            lastChangedFilesRootPath = rootPath;
            lastBaseCommit = baseCommit;
            return changedFiles;
        }
    }

    private static final class InMemorySnapshotRepository implements SnapshotRepository {

        private final ProjectSnapshot previousSnapshot;
        private ProjectSnapshot savedSnapshot;

        private InMemorySnapshotRepository(ProjectSnapshot previousSnapshot) {
            this.previousSnapshot = previousSnapshot;
        }

        @Override
        public Optional<ProjectSnapshot> findLatestByProjectId(String projectId) {
            return Optional.ofNullable(savedSnapshot != null ? savedSnapshot : previousSnapshot);
        }

        @Override
        public Optional<ProjectSnapshot> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            if (savedSnapshot != null && savedSnapshot.id().equals(snapshotId)) {
                return Optional.of(savedSnapshot);
            }
            return previousSnapshot != null && previousSnapshot.id().equals(snapshotId) ? Optional.of(previousSnapshot) : Optional.empty();
        }

        @Override
        public List<ProjectSnapshot> findByProjectId(String projectId) {
            List<ProjectSnapshot> snapshots = new ArrayList<>();
            if (savedSnapshot != null) {
                snapshots.add(savedSnapshot);
            }
            if (previousSnapshot != null) {
                snapshots.add(previousSnapshot);
            }
            return snapshots;
        }

        @Override
        public ProjectSnapshot save(ProjectSnapshot snapshot) {
            savedSnapshot = snapshot;
            return snapshot;
        }

        @Override
        public ProjectSnapshot rename(String projectId, String snapshotId, String displayName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemorySourceFileRepository implements SourceFileRepository {
        private final Map<String, List<StoredSourceFile>> filesBySnapshotId = new HashMap<>();

        private InMemorySourceFileRepository() {
        }

        private InMemorySourceFileRepository(Map<String, List<StoredSourceFile>> initialFilesBySnapshotId) {
            filesBySnapshotId.putAll(initialFilesBySnapshotId);
        }

        @Override
        public List<StoredSourceFile> saveAll(String projectId, String snapshotId, List<SourceFileRecord> files) {
            List<StoredSourceFile> storedFiles = new ArrayList<>();
            for (int index = 0; index < files.size(); index += 1) {
                SourceFileRecord file = files.get(index);
                storedFiles.add(new StoredSourceFile(
                        "file-" + index,
                        file.path(),
                        file.moduleName(),
                        file.packageName(),
                        file.contentHash(),
                        file.scope()
                ));
            }
            filesBySnapshotId.put(snapshotId, List.copyOf(storedFiles));
            return storedFiles;
        }

        @Override
        public List<StoredSourceFile> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return filesBySnapshotId.getOrDefault(snapshotId, List.of());
        }
    }

    private static final class InMemorySymbolRepository implements SymbolRepository {

        private final Map<String, List<SymbolRecord>> symbolsBySnapshotId;

        private InMemorySymbolRepository(Map<String, List<SymbolRecord>> initialSymbolsBySnapshotId) {
            this.symbolsBySnapshotId = new HashMap<>(initialSymbolsBySnapshotId);
        }

        @Override
        public void saveAll(String projectId, String snapshotId, List<SymbolRecord> symbols, Map<String, String> fileIdsByPath) {
            symbolsBySnapshotId.put(snapshotId, List.copyOf(symbols));
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return symbolsBySnapshotId.getOrDefault(snapshotId, List.of());
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotIdAndType(String projectId, String snapshotId, SymbolType symbolType) {
            return findByProjectIdAndSnapshotId(projectId, snapshotId).stream()
                    .filter(symbol -> symbol.symbolType() == symbolType)
                    .toList();
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotIdAndParentSymbolKey(String projectId, String snapshotId, String parentSymbolKey) {
            return findByProjectIdAndSnapshotId(projectId, snapshotId).stream()
                    .filter(symbol -> parentSymbolKey.equals(symbol.parentSymbolKey()))
                    .toList();
        }
    }

    private static final class InMemoryRelationRepository implements RelationRepository {

        private final Map<String, List<RelationRecord>> relationsBySnapshotId;

        private InMemoryRelationRepository(Map<String, List<RelationRecord>> initialRelationsBySnapshotId) {
            this.relationsBySnapshotId = new HashMap<>(initialRelationsBySnapshotId);
        }

        @Override
        public void saveAll(String projectId, String snapshotId, List<RelationRecord> relations, Map<String, String> fileIdsByPath) {
            relationsBySnapshotId.put(snapshotId, List.copyOf(relations));
        }

        @Override
        public List<RelationRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return relationsBySnapshotId.getOrDefault(snapshotId, List.of());
        }
    }

    private static final class InMemorySymbolChangeRepository implements SymbolChangeRepository {

        private final List<StoredSymbolChange> savedChanges = new ArrayList<>();

        @Override
        public void saveAll(List<StoredSymbolChange> changes) {
            savedChanges.clear();
            savedChanges.addAll(changes);
        }

        @Override
        public List<StoredSymbolChange> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return List.copyOf(savedChanges);
        }
    }

    private static final class NoOpProjectRepository implements ProjectRepository {
        @Override
        public Optional<RegisteredProject> findByRootPath(String rootPath) {
            return Optional.empty();
        }

        @Override
        public Optional<RegisteredProject> findById(String projectId) {
            return Optional.empty();
        }

        @Override
        public List<RegisteredProject> findAll() {
            return List.of();
        }

        @Override
        public RegisteredProject save(RegisteredProject project) {
            return project;
        }

        @Override
        public void deleteById(String projectId) {
        }
    }

    private static final class NoOpBuildToolDetector extends BuildToolDetector {
        @Override
        public String detect(Path rootPath) {
            return "maven";
        }
    }
}
