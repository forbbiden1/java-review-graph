package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import java.util.HashSet;
import java.util.List;
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
        List<SymbolRecord> types = symbolRepository.findByProjectIdAndSnapshotIdAndType(projectId, resolvedSnapshotId, SymbolType.TYPE);
        Set<String> typeKeys = types.stream()
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        return relationRepository.findByProjectIdAndSnapshotId(projectId, resolvedSnapshotId).stream()
                .filter(relation -> relation.relationType() == RelationType.EXTENDS
                        || relation.relationType() == RelationType.IMPLEMENTS
                        || relation.relationType() == RelationType.USES_TYPE)
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
        Set<String> methodKeys = symbolRepository.findByProjectIdAndSnapshotIdAndParentSymbolKey(projectId, resolvedSnapshotId, classId)
                .stream()
                .map(SymbolRecord::symbolKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        return relationRepository.findByProjectIdAndSnapshotId(projectId, resolvedSnapshotId).stream()
                .filter(relation -> relation.relationType() == RelationType.CALLS)
                .filter(relation -> methodKeys.contains(relation.sourceSymbolKey()) && methodKeys.contains(relation.targetSymbolKey()))
                .toList();
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
