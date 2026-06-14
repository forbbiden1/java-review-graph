package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import java.util.List;
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
}
