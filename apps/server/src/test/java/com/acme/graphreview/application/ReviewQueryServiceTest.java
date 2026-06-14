package com.acme.graphreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewQueryServiceTest {

    @Test
    void getClassGraphEdgesKeepsOnlyIndexedTypeToTypeEdges() {
        RegisteredProject project = project();
        ProjectSnapshot snapshot = snapshot(project.id());
        SymbolRecord serviceType = typeSymbol("type:root:demo.Service", "demo.Service", "Service");
        SymbolRecord controllerType = typeSymbol("type:root:demo.Controller", "demo.Controller", "Controller");

        CapturingSymbolRepository symbolRepository = new CapturingSymbolRepository(
                List.of(serviceType, controllerType),
                Map.of()
        );
        CapturingRelationRepository relationRepository = new CapturingRelationRepository();
        RelationRecord localExtends = relation(serviceType.symbolKey(), controllerType.symbolKey(), RelationType.EXTENDS);
        RelationRecord externalUse = relation(serviceType.symbolKey(), "external:type:java.util.List", RelationType.USES_TYPE);
        relationRepository.typeQueryResult = List.of(localExtends, externalUse);

        ReviewQueryService service = new ReviewQueryService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(snapshot),
                symbolRepository,
                relationRepository,
                new NoOpSymbolChangeRepository()
        );

        List<RelationRecord> edges = service.getClassGraphEdges(project.id(), snapshot.id());

        assertEquals(List.of(RelationType.EXTENDS, RelationType.IMPLEMENTS, RelationType.USES_TYPE), relationRepository.lastTypes);
        assertEquals(List.of(localExtends), edges);
    }

    @Test
    void getMethodGraphEdgesQueriesCallsForMethodsBelongingToClass() {
        RegisteredProject project = project();
        ProjectSnapshot snapshot = snapshot(project.id());
        String classId = "type:root:demo.Service";
        SymbolRecord loadMethod = methodSymbol("method:demo.Service#load()", classId, "load");
        SymbolRecord saveMethod = methodSymbol("method:demo.Service#save()", classId, "save");

        CapturingSymbolRepository symbolRepository = new CapturingSymbolRepository(
                List.of(),
                Map.of(classId, List.of(loadMethod, saveMethod))
        );
        CapturingRelationRepository relationRepository = new CapturingRelationRepository();
        RelationRecord callEdge = relation(loadMethod.symbolKey(), saveMethod.symbolKey(), RelationType.CALLS);
        relationRepository.symbolKeyQueryResult = List.of(callEdge);

        ReviewQueryService service = new ReviewQueryService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(snapshot),
                symbolRepository,
                relationRepository,
                new NoOpSymbolChangeRepository()
        );

        List<RelationRecord> edges = service.getMethodGraphEdges(project.id(), snapshot.id(), classId);

        assertEquals(RelationType.CALLS, relationRepository.lastRelationType);
        assertEquals(Set.of(loadMethod.symbolKey(), saveMethod.symbolKey()), relationRepository.lastSymbolKeys);
        assertEquals(List.of(callEdge), edges);
    }

    @Test
    void getMethodGraphEdgesReturnsEmptyWhenClassHasNoMethods() {
        RegisteredProject project = project();
        ProjectSnapshot snapshot = snapshot(project.id());
        String classId = "type:root:demo.Service";

        CapturingSymbolRepository symbolRepository = new CapturingSymbolRepository(List.of(), Map.of(classId, List.of()));
        CapturingRelationRepository relationRepository = new CapturingRelationRepository();

        ReviewQueryService service = new ReviewQueryService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(snapshot),
                symbolRepository,
                relationRepository,
                new NoOpSymbolChangeRepository()
        );

        List<RelationRecord> edges = service.getMethodGraphEdges(project.id(), snapshot.id(), classId);

        assertTrue(edges.isEmpty());
        assertEquals(0, relationRepository.symbolKeyQueryCount);
    }

    @Test
    void findSymbolPathReturnsShortestTraceWithTraversalDirection() {
        RegisteredProject project = project();
        ProjectSnapshot snapshot = snapshot(project.id());
        SymbolRecord controllerType = typeSymbol("type:root:demo.Controller", "demo.Controller", "Controller");
        SymbolRecord serviceType = typeSymbol("type:root:demo.Service", "demo.Service", "Service");
        SymbolRecord repositoryType = typeSymbol("type:root:demo.Repository", "demo.Repository", "Repository");

        CapturingSymbolRepository symbolRepository = new CapturingSymbolRepository(
                List.of(controllerType, serviceType, repositoryType),
                Map.of()
        );
        CapturingRelationRepository relationRepository = new CapturingRelationRepository();
        RelationRecord controllerUsesService = relation(controllerType.symbolKey(), serviceType.symbolKey(), RelationType.USES_TYPE);
        RelationRecord repositoryUsedByService = relation(repositoryType.symbolKey(), serviceType.symbolKey(), RelationType.USES_TYPE);
        relationRepository.typeQueryResult = List.of(controllerUsesService, repositoryUsedByService);

        ReviewQueryService service = new ReviewQueryService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(snapshot),
                symbolRepository,
                relationRepository,
                new NoOpSymbolChangeRepository()
        );

        var result = service.findSymbolPath(project.id(), snapshot.id(), controllerType.symbolKey(), repositoryType.symbolKey(), 4);

        assertTrue(result.found());
        assertEquals(snapshot.id(), result.snapshotId());
        assertEquals(4, result.maxDepth());
        assertEquals(
                List.of(controllerType.symbolKey(), serviceType.symbolKey(), repositoryType.symbolKey()),
                result.nodes().stream().map(ReviewQueryService.SymbolPathNode::symbolKey).toList()
        );
        assertEquals(2, result.segments().size());
        assertEquals(controllerType.symbolKey(), result.segments().get(0).sourceSymbolKey());
        assertEquals(serviceType.symbolKey(), result.segments().get(0).targetSymbolKey());
        assertEquals(serviceType.symbolKey(), result.segments().get(1).sourceSymbolKey());
        assertEquals(repositoryType.symbolKey(), result.segments().get(1).targetSymbolKey());
        assertEquals(
                List.of(RelationType.EXTENDS, RelationType.IMPLEMENTS, RelationType.USES_TYPE, RelationType.CALLS, RelationType.OVERRIDES),
                relationRepository.lastTypes
        );
    }

    @Test
    void findSymbolPathRespectsDepthLimit() {
        RegisteredProject project = project();
        ProjectSnapshot snapshot = snapshot(project.id());
        SymbolRecord controllerType = typeSymbol("type:root:demo.Controller", "demo.Controller", "Controller");
        SymbolRecord serviceType = typeSymbol("type:root:demo.Service", "demo.Service", "Service");
        SymbolRecord repositoryType = typeSymbol("type:root:demo.Repository", "demo.Repository", "Repository");

        CapturingSymbolRepository symbolRepository = new CapturingSymbolRepository(
                List.of(controllerType, serviceType, repositoryType),
                Map.of()
        );
        CapturingRelationRepository relationRepository = new CapturingRelationRepository();
        relationRepository.typeQueryResult = List.of(
                relation(controllerType.symbolKey(), serviceType.symbolKey(), RelationType.USES_TYPE),
                relation(serviceType.symbolKey(), repositoryType.symbolKey(), RelationType.CALLS)
        );

        ReviewQueryService service = new ReviewQueryService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(snapshot),
                symbolRepository,
                relationRepository,
                new NoOpSymbolChangeRepository()
        );

        var result = service.findSymbolPath(project.id(), snapshot.id(), controllerType.symbolKey(), repositoryType.symbolKey(), 1);

        assertFalse(result.found());
        assertTrue(result.nodes().isEmpty());
        assertTrue(result.segments().isEmpty());
    }

    @Test
    void findSymbolPathReturnsNotFoundForSymbolsOutsideSnapshot() {
        RegisteredProject project = project();
        ProjectSnapshot snapshot = snapshot(project.id());
        SymbolRecord controllerType = typeSymbol("type:root:demo.Controller", "demo.Controller", "Controller");

        CapturingSymbolRepository symbolRepository = new CapturingSymbolRepository(List.of(controllerType), Map.of());
        CapturingRelationRepository relationRepository = new CapturingRelationRepository();

        ReviewQueryService service = new ReviewQueryService(
                new StubProjectService(project),
                new InMemorySnapshotRepository(snapshot),
                symbolRepository,
                relationRepository,
                new NoOpSymbolChangeRepository()
        );

        var result = service.findSymbolPath(project.id(), snapshot.id(), controllerType.symbolKey(), "type:root:demo.Missing", 4);

        assertFalse(result.found());
        assertEquals(0, relationRepository.lastTypes.size());
        assertTrue(result.note().contains("not present"));
    }

    private static RegisteredProject project() {
        return new RegisteredProject(
                "project-1",
                "demo",
                Path.of(".").toAbsolutePath().normalize().toString(),
                "maven",
                Instant.now(),
                Instant.now()
        );
    }

    private static ProjectSnapshot snapshot(String projectId) {
        return new ProjectSnapshot(
                "snapshot-1",
                projectId,
                null,
                "manual",
                null,
                null,
                "snapshot-1",
                "completed",
                Instant.now()
        );
    }

    private static SymbolRecord typeSymbol(String symbolKey, String qualifiedName, String name) {
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
                "api-" + name,
                "impl-" + name,
                ChangeStatus.UNCHANGED
        );
    }

    private static SymbolRecord methodSymbol(String symbolKey, String parentSymbolKey, String name) {
        return new SymbolRecord(
                symbolKey,
                SymbolType.METHOD,
                SymbolKind.METHOD,
                parentSymbolKey,
                name,
                "demo",
                "demo.Service#" + name,
                name,
                "demo.Service#" + name + "()",
                "src/main/java/demo/Service.java",
                5,
                8,
                "api-" + name,
                "impl-" + name,
                ChangeStatus.UNCHANGED
        );
    }

    private static RelationRecord relation(String sourceSymbolKey, String targetSymbolKey, RelationType relationType) {
        return new RelationRecord(sourceSymbolKey, targetSymbolKey, relationType, "exact", "src/main/java/demo/Service.java", 6);
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

    private static final class InMemorySnapshotRepository implements SnapshotRepository {

        private final ProjectSnapshot snapshot;

        private InMemorySnapshotRepository(ProjectSnapshot snapshot) {
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

    private static final class CapturingSymbolRepository implements SymbolRepository {

        private final List<SymbolRecord> typeSymbols;
        private final Map<String, List<SymbolRecord>> methodsByParentSymbolKey;

        private CapturingSymbolRepository(List<SymbolRecord> typeSymbols, Map<String, List<SymbolRecord>> methodsByParentSymbolKey) {
            this.typeSymbols = List.copyOf(typeSymbols);
            this.methodsByParentSymbolKey = Map.copyOf(methodsByParentSymbolKey);
        }

        @Override
        public void saveAll(String projectId, String snapshotId, List<SymbolRecord> symbols, Map<String, String> fileIdsByPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return java.util.stream.Stream.concat(
                    typeSymbols.stream(),
                    methodsByParentSymbolKey.values().stream().flatMap(List::stream)
            ).toList();
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotIdAndType(String projectId, String snapshotId, SymbolType symbolType) {
            return symbolType == SymbolType.TYPE ? typeSymbols : List.of();
        }

        @Override
        public List<SymbolRecord> findByProjectIdAndSnapshotIdAndParentSymbolKey(String projectId, String snapshotId, String parentSymbolKey) {
            return methodsByParentSymbolKey.getOrDefault(parentSymbolKey, List.of());
        }
    }

    private static final class CapturingRelationRepository implements RelationRepository {

        private List<RelationRecord> typeQueryResult = List.of();
        private List<RelationRecord> symbolKeyQueryResult = List.of();
        private List<RelationType> lastTypes = List.of();
        private RelationType lastRelationType;
        private Set<String> lastSymbolKeys = Set.of();
        private int symbolKeyQueryCount;

        @Override
        public void saveAll(String projectId, String snapshotId, List<RelationRecord> relations, Map<String, String> fileIdsByPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RelationRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return List.of();
        }

        @Override
        public List<RelationRecord> findByProjectIdAndSnapshotIdAndTypes(
                String projectId,
                String snapshotId,
                List<RelationType> relationTypes
        ) {
            lastTypes = List.copyOf(relationTypes);
            return typeQueryResult;
        }

        @Override
        public List<RelationRecord> findByProjectIdAndSnapshotIdAndSymbolKeys(
                String projectId,
                String snapshotId,
                RelationType relationType,
                Set<String> sourceOrTargetSymbolKeys
        ) {
            symbolKeyQueryCount += 1;
            lastRelationType = relationType;
            lastSymbolKeys = Set.copyOf(sourceOrTargetSymbolKeys);
            return symbolKeyQueryResult;
        }
    }

    private static final class NoOpSymbolChangeRepository implements SymbolChangeRepository {
        @Override
        public void saveAll(List<StoredSymbolChange> changes) {
        }

        @Override
        public List<StoredSymbolChange> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
            return List.of();
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
