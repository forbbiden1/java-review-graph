package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReviewQueryService {

    private final ProjectService projectService;
    private final SnapshotRepository snapshotRepository;
    private final SymbolRepository symbolRepository;
    private final RelationRepository relationRepository;
    private final SymbolChangeRepository symbolChangeRepository;

    public ReviewQueryService(
            ProjectService projectService,
            SnapshotRepository snapshotRepository,
            SymbolRepository symbolRepository,
            RelationRepository relationRepository,
            SymbolChangeRepository symbolChangeRepository
    ) {
        this.projectService = projectService;
        this.snapshotRepository = snapshotRepository;
        this.symbolRepository = symbolRepository;
        this.relationRepository = relationRepository;
        this.symbolChangeRepository = symbolChangeRepository;
    }

    public List<SymbolRecord> getClassGraphNodes(String projectId, String snapshotId) {
        String resolvedSnapshotId = resolveSnapshotId(projectId, snapshotId);
        return symbolRepository.findByProjectIdAndSnapshotIdAndType(projectId, resolvedSnapshotId, SymbolType.TYPE);
    }

    public List<RelationRecord> getClassGraphEdges(String projectId, String snapshotId) {
        String resolvedSnapshotId = resolveSnapshotId(projectId, snapshotId);
        var typeKeys = symbolRepository.findByProjectIdAndSnapshotIdAndType(projectId, resolvedSnapshotId, SymbolType.TYPE)
                .stream()
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toSet());
        if (typeKeys.isEmpty()) {
            return List.of();
        }
        return relationRepository.findByProjectIdAndSnapshotIdAndTypes(
                projectId,
                resolvedSnapshotId,
                List.of(RelationType.EXTENDS, RelationType.IMPLEMENTS, RelationType.USES_TYPE)
        ).stream()
                .filter(relation -> typeKeys.contains(relation.sourceSymbolKey()) && typeKeys.contains(relation.targetSymbolKey()))
                .toList();
    }

    public List<StoredSymbolChange> getChanges(String projectId, String snapshotId) {
        String resolvedSnapshotId = resolveSnapshotId(projectId, snapshotId);
        return symbolChangeRepository.findByProjectIdAndSnapshotId(projectId, resolvedSnapshotId);
    }

    public List<SymbolRecord> getMethodGraphNodes(String projectId, String snapshotId, String classId) {
        String resolvedSnapshotId = resolveSnapshotId(projectId, snapshotId);
        return symbolRepository.findByProjectIdAndSnapshotIdAndParentSymbolKey(projectId, resolvedSnapshotId, classId);
    }

    public List<RelationRecord> getMethodGraphEdges(String projectId, String snapshotId, String classId) {
        String resolvedSnapshotId = resolveSnapshotId(projectId, snapshotId);
        var methodKeys = symbolRepository.findByProjectIdAndSnapshotIdAndParentSymbolKey(projectId, resolvedSnapshotId, classId)
                .stream()
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toSet());
        if (methodKeys.isEmpty()) {
            return List.of();
        }
        return relationRepository.findByProjectIdAndSnapshotIdAndSymbolKeys(
                projectId,
                resolvedSnapshotId,
                RelationType.CALLS,
                methodKeys
        );
    }

    public SymbolPathResult findSymbolPath(
            String projectId,
            String snapshotId,
            String sourceSymbolKey,
            String targetSymbolKey,
            int maxDepth
    ) {
        String resolvedSnapshotId = resolveSnapshotId(projectId, snapshotId);
        if (sourceSymbolKey == null || sourceSymbolKey.isBlank() || targetSymbolKey == null || targetSymbolKey.isBlank()) {
            return SymbolPathResult.notFound(resolvedSnapshotId, sourceSymbolKey, targetSymbolKey, maxDepth, "Source and target symbol keys are required.");
        }

        int boundedMaxDepth = Math.max(1, Math.min(maxDepth <= 0 ? 4 : maxDepth, 8));
        Map<String, SymbolRecord> symbolsByKey = symbolRepository.findByProjectIdAndSnapshotId(projectId, resolvedSnapshotId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left, LinkedHashMap::new));
        if (!symbolsByKey.containsKey(sourceSymbolKey) || !symbolsByKey.containsKey(targetSymbolKey)) {
            return SymbolPathResult.notFound(
                    resolvedSnapshotId,
                    sourceSymbolKey,
                    targetSymbolKey,
                    boundedMaxDepth,
                    "Source or target symbol is not present in the selected snapshot."
            );
        }

        if (sourceSymbolKey.equals(targetSymbolKey)) {
            SymbolPathNode node = SymbolPathNode.from(symbolsByKey.get(sourceSymbolKey));
            return new SymbolPathResult(
                    resolvedSnapshotId,
                    sourceSymbolKey,
                    targetSymbolKey,
                    boundedMaxDepth,
                    true,
                    List.of(node),
                    List.of(),
                    "Source and target are the same symbol."
            );
        }

        List<RelationRecord> relations = relationRepository.findByProjectIdAndSnapshotIdAndTypes(
                projectId,
                resolvedSnapshotId,
                List.of(RelationType.EXTENDS, RelationType.IMPLEMENTS, RelationType.USES_TYPE, RelationType.CALLS, RelationType.OVERRIDES)
        );
        Map<String, List<PathConnection>> adjacency = buildUndirectedAdjacency(relations, symbolsByKey.keySet());
        List<PathStep> pathSteps = findShortestPath(sourceSymbolKey, targetSymbolKey, adjacency, boundedMaxDepth);
        if (pathSteps.isEmpty()) {
            return SymbolPathResult.notFound(
                    resolvedSnapshotId,
                    sourceSymbolKey,
                    targetSymbolKey,
                    boundedMaxDepth,
                    "No relation path was found within depth " + boundedMaxDepth + "."
            );
        }

        List<SymbolPathNode> nodes = new ArrayList<>();
        nodes.add(SymbolPathNode.from(symbolsByKey.get(sourceSymbolKey)));
        String currentKey = sourceSymbolKey;
        for (PathStep step : pathSteps) {
            currentKey = step.targetSymbolKey();
            nodes.add(SymbolPathNode.from(symbolsByKey.get(currentKey)));
        }

        return new SymbolPathResult(
                resolvedSnapshotId,
                sourceSymbolKey,
                targetSymbolKey,
                boundedMaxDepth,
                true,
                List.copyOf(nodes),
                List.copyOf(pathSteps).stream().map(PathStep::toSegment).toList(),
                "Found relation path with " + pathSteps.size() + " edge(s)."
        );
    }

    private Map<String, List<PathConnection>> buildUndirectedAdjacency(List<RelationRecord> relations, Set<String> indexedSymbolKeys) {
        Map<String, List<PathConnection>> adjacency = new LinkedHashMap<>();
        for (RelationRecord relation : relations) {
            if (!indexedSymbolKeys.contains(relation.sourceSymbolKey()) || !indexedSymbolKeys.contains(relation.targetSymbolKey())) {
                continue;
            }
            PathConnection connection = new PathConnection(relation);
            adjacency.computeIfAbsent(relation.sourceSymbolKey(), ignored -> new ArrayList<>()).add(connection);
            adjacency.computeIfAbsent(relation.targetSymbolKey(), ignored -> new ArrayList<>()).add(connection);
        }
        return adjacency;
    }

    private List<PathStep> findShortestPath(
            String sourceSymbolKey,
            String targetSymbolKey,
            Map<String, List<PathConnection>> adjacency,
            int maxDepth
    ) {
        ArrayDeque<PathSearchState> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(new PathSearchState(sourceSymbolKey, List.of()));
        visited.add(sourceSymbolKey);

        while (!queue.isEmpty()) {
            PathSearchState state = queue.removeFirst();
            if (state.path().size() >= maxDepth) {
                continue;
            }
            for (PathConnection connection : adjacency.getOrDefault(state.symbolKey(), List.of())) {
                String nextSymbolKey = connection.nextSymbolKey(state.symbolKey());
                if (!visited.add(nextSymbolKey)) {
                    continue;
                }
                List<PathStep> nextPath = new ArrayList<>(state.path());
                nextPath.add(new PathStep(state.symbolKey(), nextSymbolKey, connection.relation()));
                if (targetSymbolKey.equals(nextSymbolKey)) {
                    return List.copyOf(nextPath);
                }
                queue.addLast(new PathSearchState(nextSymbolKey, List.copyOf(nextPath)));
            }
        }
        return List.of();
    }

    public String resolveSnapshotId(String projectId, String snapshotId) {
        projectService.getProject(projectId);
        if (snapshotId != null && !snapshotId.isBlank()) {
            return snapshotRepository.findByProjectIdAndSnapshotId(projectId, snapshotId)
                    .map(ProjectSnapshot::id)
                    .orElseThrow(() -> new SnapshotNotFoundException(projectId));
        }
        ProjectSnapshot latestSnapshot = snapshotRepository.findLatestByProjectId(projectId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
        return latestSnapshot.id();
    }

    private record PathSearchState(String symbolKey, List<PathStep> path) {
    }

    private record PathConnection(RelationRecord relation) {
        private String nextSymbolKey(String currentSymbolKey) {
            return Objects.equals(currentSymbolKey, relation.sourceSymbolKey())
                    ? relation.targetSymbolKey()
                    : relation.sourceSymbolKey();
        }
    }

    private record PathStep(String sourceSymbolKey, String targetSymbolKey, RelationRecord relation) {
        private SymbolPathSegment toSegment() {
            return new SymbolPathSegment(
                    sourceSymbolKey,
                    targetSymbolKey,
                    relation.relationType(),
                    relation.filePath(),
                    relation.sourceLine()
            );
        }
    }

    public record SymbolPathResult(
            String snapshotId,
            String sourceSymbolKey,
            String targetSymbolKey,
            int maxDepth,
            boolean found,
            List<SymbolPathNode> nodes,
            List<SymbolPathSegment> segments,
            String note
    ) {
        private static SymbolPathResult notFound(
                String snapshotId,
                String sourceSymbolKey,
                String targetSymbolKey,
                int maxDepth,
                String note
        ) {
            return new SymbolPathResult(snapshotId, sourceSymbolKey, targetSymbolKey, maxDepth, false, List.of(), List.of(), note);
        }
    }

    public record SymbolPathNode(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String kind,
            String status
    ) {
        private static SymbolPathNode from(SymbolRecord symbol) {
            return new SymbolPathNode(
                    symbol.symbolKey(),
                    symbol.qualifiedName(),
                    symbol.displayName(),
                    symbol.kind().name().toLowerCase(),
                    symbol.changeStatus().name().toLowerCase()
            );
        }
    }

    public record SymbolPathSegment(
            String sourceSymbolKey,
            String targetSymbolKey,
            RelationType relationType,
            String filePath,
            Integer sourceLine
    ) {
    }
}
