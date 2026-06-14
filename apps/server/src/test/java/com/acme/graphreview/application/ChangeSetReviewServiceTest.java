package com.acme.graphreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewCommand;
import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewResult;
import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.GitChangedFiles;
import com.acme.graphreview.infrastructure.GitSnapshotMetadataResolver;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeSetReviewServiceTest {

    @Test
    void reviewsGitChangeSetAgainstSelectedSnapshot() {
        RegisteredProject project = new RegisteredProject(
                "project-1",
                "demo",
                Path.of(".").toAbsolutePath().normalize().toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
        ProjectSnapshot snapshot = new ProjectSnapshot(
                "snapshot-1",
                project.id(),
                null,
                "git",
                "abcdef123456",
                "Review baseline",
                "Review Baseline",
                "completed",
                Instant.now()
        );

        SymbolRecord changedType = symbol("type:demo.Service", "demo.Service", "Service", "src/main/java/demo/Service.java", ChangeStatus.MODIFIED_API);
        SymbolRecord impactedType = symbol("type:demo.Controller", "demo.Controller", "Controller", "src/main/java/demo/Controller.java", ChangeStatus.IMPACTED);

        ChangeSetReviewService service = new ChangeSetReviewService(
                new StubProjectService(project),
                new StubSnapshotRepository(snapshot),
                new StubSourceFileRepository(List.of(
                        storedFile("src/main/java/demo/Service.java"),
                        storedFile("src/main/java/demo/Controller.java")
                )),
                new StubSymbolRepository(List.of(changedType, impactedType)),
                new StubSymbolChangeRepository(List.of(
                        new StoredSymbolChange("change-1", project.id(), snapshot.id(), impactedType.symbolKey(), null, impactedType.symbolKey(), "impacted", "one-hop")
                )),
                new StubGitSnapshotMetadataResolver(GitChangedFiles.available(
                        List.of("src/main/java/demo/Service.java"),
                        "Incremental Git diff collected 1 changed path(s) from commit abcdef12 to the current workspace state."
                ))
        );

        ChangeSetReviewResult result = service.reviewChangeSet(
                project.id(),
                new ChangeSetReviewCommand(snapshot.id(), "git", List.of())
        );

        assertEquals(snapshot.id(), result.snapshotId());
        assertEquals(List.of("src/main/java/demo/Service.java"), result.changedFiles());
        assertEquals(1, result.changedSymbols().size());
        assertEquals(changedType.symbolKey(), result.changedSymbols().get(0).symbolKey());
        assertEquals(1, result.impactedSymbols().size());
        assertEquals(impactedType.symbolKey(), result.impactedSymbols().get(0).symbolKey());
        assertEquals("medium", result.risk().riskLevel());
        assertEquals(4, result.risk().riskScore());
        assertTrue(result.risk().reasons().contains("Public API or deleted symbol changed."));
        assertTrue(result.risk().reasons().contains("One-hop impacted symbols were found."));
        assertEquals(changedType.symbolKey(), result.reviewTargets().get(0).symbolKey());
        assertTrue(result.summary().contains("1 changed file(s), 1 changed symbol(s), and 1 impacted symbol(s). Risk level: medium."));
    }

    private static SymbolRecord symbol(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String filePath,
            ChangeStatus status
    ) {
        return new SymbolRecord(
                symbolKey,
                SymbolType.TYPE,
                SymbolKind.CLASS,
                null,
                displayName,
                "demo",
                qualifiedName,
                displayName,
                qualifiedName,
                filePath,
                1,
                10,
                "api-" + displayName,
                "impl-" + displayName,
                status
        );
    }

    private static StoredSourceFile storedFile(String path) {
        return new StoredSourceFile("file-" + path, path, "root", "demo", "hash-" + path, "main");
    }

    private static final class StubProjectService extends ProjectService {
        private final RegisteredProject project;

        private StubProjectService(RegisteredProject project) {
            super(new NoOpProjectRepository(), new BuildToolDetector());
            this.project = project;
        }

        @Override
        public RegisteredProject getProject(String projectId) {
            return project;
        }
    }

    private static final class StubSnapshotRepository implements SnapshotRepository {
        private final ProjectSnapshot snapshot;

        private StubSnapshotRepository(ProjectSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<ProjectSnapshot> findLatestByProjectId(String projectId) {
            return Optional.of(snapshot);
        }

        @Override
        public Optional<ProjectSnapshot> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return snapshot.id().equals(snapshotId) ? Optional.of(snapshot) : Optional.empty();
        }

        @Override
        public List<ProjectSnapshot> findByProjectId(String projectId) {
            return List.of(snapshot);
        }

        @Override
        public ProjectSnapshot save(ProjectSnapshot snapshot) {
            throw new UnsupportedOperationException();
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

    private static final class StubSourceFileRepository implements SourceFileRepository {
        private final List<StoredSourceFile> files;

        private StubSourceFileRepository(List<StoredSourceFile> files) {
            this.files = files;
        }

        @Override
        public List<StoredSourceFile> saveAll(String projectId, String snapshotId, List<com.acme.model.analysis.SourceFileRecord> files) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StoredSourceFile> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return files;
        }
    }

    private static final class StubSymbolRepository implements SymbolRepository {
        private final List<SymbolRecord> symbols;

        private StubSymbolRepository(List<SymbolRecord> symbols) {
            this.symbols = symbols;
        }

        @Override
        public void saveAll(String projectId, String snapshotId, List<SymbolRecord> symbols, Map<String, String> fileIdsByPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return symbols;
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotIdAndType(String projectId, String snapshotId, SymbolType symbolType) {
            return List.of();
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotIdAndParentSymbolKey(String projectId, String snapshotId, String parentSymbolKey) {
            return List.of();
        }
    }

    private static final class StubSymbolChangeRepository implements SymbolChangeRepository {
        private final List<StoredSymbolChange> changes;

        private StubSymbolChangeRepository(List<StoredSymbolChange> changes) {
            this.changes = changes;
        }

        @Override
        public void saveAll(List<StoredSymbolChange> changes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StoredSymbolChange> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return changes;
        }
    }

    private static final class StubGitSnapshotMetadataResolver extends GitSnapshotMetadataResolver {
        private final GitChangedFiles changedFiles;

        private StubGitSnapshotMetadataResolver(GitChangedFiles changedFiles) {
            this.changedFiles = changedFiles;
        }

        @Override
        public GitChangedFiles resolveChangedFiles(Path rootPath, String baseCommit) {
            return changedFiles;
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
}
