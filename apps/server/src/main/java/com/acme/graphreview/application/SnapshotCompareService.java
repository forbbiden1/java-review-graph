package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SnapshotCompareService {

    private static final Set<RelationType> COMPARABLE_RELATION_TYPES = EnumSet.of(
            RelationType.EXTENDS,
            RelationType.IMPLEMENTS,
            RelationType.USES_TYPE,
            RelationType.CALLS,
            RelationType.OVERRIDES
    );

    private final ProjectService projectService;
    private final SnapshotRepository snapshotRepository;
    private final SymbolRepository symbolRepository;
    private final RelationRepository relationRepository;

    public SnapshotCompareService(
            ProjectService projectService,
            SnapshotRepository snapshotRepository,
            SymbolRepository symbolRepository,
            RelationRepository relationRepository
    ) {
        this.projectService = projectService;
        this.snapshotRepository = snapshotRepository;
        this.symbolRepository = symbolRepository;
        this.relationRepository = relationRepository;
    }

    public SnapshotCompareResult compareSnapshots(String projectId, String baseSnapshotId, String targetSnapshotId) {
        projectService.getProject(projectId);
        if (baseSnapshotId == null || baseSnapshotId.isBlank()) {
            throw new ProjectValidationException("Base snapshot id is required.");
        }
        if (targetSnapshotId == null || targetSnapshotId.isBlank()) {
            throw new ProjectValidationException("Target snapshot id is required.");
        }
        if (baseSnapshotId.equals(targetSnapshotId)) {
            throw new ProjectValidationException("Base and target snapshots must be different.");
        }

        ProjectSnapshot baseSnapshot = snapshotRepository.findByProjectIdAndSnapshotId(projectId, baseSnapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
        ProjectSnapshot targetSnapshot = snapshotRepository.findByProjectIdAndSnapshotId(projectId, targetSnapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
        List<SymbolRecord> baseSymbols = symbolRepository.findByProjectIdAndSnapshotId(projectId, baseSnapshot.id());
        List<SymbolRecord> targetSymbols = symbolRepository.findByProjectIdAndSnapshotId(projectId, targetSnapshot.id());
        List<RelationRecord> baseRelations = relationRepository.findByProjectIdAndSnapshotId(projectId, baseSnapshot.id());
        List<RelationRecord> targetRelations = relationRepository.findByProjectIdAndSnapshotId(projectId, targetSnapshot.id());

        Map<String, SymbolRecord> baseByKey = toSymbolMap(baseSymbols);
        Map<String, SymbolRecord> targetByKey = toSymbolMap(targetSymbols);
        List<SnapshotSymbolDiff> diffs = new ArrayList<>();

        for (SymbolRecord targetSymbol : targetSymbols) {
            SymbolRecord baseSymbol = baseByKey.get(targetSymbol.symbolKey());
            if (baseSymbol == null) {
                diffs.add(SnapshotSymbolDiff.from("added", "Symbol exists only in the target snapshot.", targetSymbol));
                continue;
            }
            if (!Objects.equals(baseSymbol.apiHash(), targetSymbol.apiHash())) {
                diffs.add(SnapshotSymbolDiff.from("modified_api", "API hash differs between snapshots.", targetSymbol));
                continue;
            }
            if (!Objects.equals(baseSymbol.implHash(), targetSymbol.implHash())) {
                diffs.add(SnapshotSymbolDiff.from("modified_impl", "Implementation hash differs between snapshots.", targetSymbol));
            }
        }

        for (SymbolRecord baseSymbol : baseSymbols) {
            if (!targetByKey.containsKey(baseSymbol.symbolKey())) {
                diffs.add(SnapshotSymbolDiff.from("deleted", "Symbol exists only in the base snapshot.", baseSymbol));
            }
        }

        List<SnapshotSymbolDiff> sortedDiffs = diffs.stream()
                .sorted(Comparator.comparing(SnapshotSymbolDiff::changeType).thenComparing(SnapshotSymbolDiff::qualifiedName))
                .toList();
        SnapshotCompareSummary summary = summarize(baseByKey.size(), targetByKey.size(), baseByKey, targetByKey, sortedDiffs);
        List<SnapshotRelationDiff> relationDiffs = compareRelations(baseRelations, targetRelations, baseByKey, targetByKey);
        SnapshotCompareRelationSummary relationSummary = summarizeRelations(baseRelations, targetRelations, relationDiffs);

        return new SnapshotCompareResult(
                projectId,
                SnapshotRef.from(baseSnapshot),
                SnapshotRef.from(targetSnapshot),
                summary,
                sortedDiffs,
                relationSummary,
                relationDiffs,
                buildCompareNote(baseSnapshot, targetSnapshot, summary, relationSummary)
        );
    }

    private Map<String, SymbolRecord> toSymbolMap(List<SymbolRecord> symbols) {
        return symbols.stream()
                .collect(Collectors.toMap(
                        SymbolRecord::symbolKey,
                        symbol -> symbol,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private SnapshotCompareSummary summarize(
            int baseSymbolCount,
            int targetSymbolCount,
            Map<String, SymbolRecord> baseByKey,
            Map<String, SymbolRecord> targetByKey,
            List<SnapshotSymbolDiff> diffs
    ) {
        Map<String, Long> countsByType = diffs.stream()
                .collect(Collectors.groupingBy(SnapshotSymbolDiff::changeType, Collectors.counting()));
        int added = countsByType.getOrDefault("added", 0L).intValue();
        int deleted = countsByType.getOrDefault("deleted", 0L).intValue();
        int modifiedApi = countsByType.getOrDefault("modified_api", 0L).intValue();
        int modifiedImpl = countsByType.getOrDefault("modified_impl", 0L).intValue();
        int totalComparedSymbols = countDistinctSymbolKeys(baseByKey, targetByKey);
        int unchanged = Math.max(0, totalComparedSymbols - added - deleted - modifiedApi - modifiedImpl);
        return new SnapshotCompareSummary(
                baseSymbolCount,
                targetSymbolCount,
                totalComparedSymbols,
                added,
                deleted,
                modifiedApi,
                modifiedImpl,
                unchanged,
                diffs.size()
        );
    }

    private int countDistinctSymbolKeys(Map<String, SymbolRecord> baseByKey, Map<String, SymbolRecord> targetByKey) {
        long targetOnlyCount = targetByKey.keySet().stream()
                .filter(symbolKey -> !baseByKey.containsKey(symbolKey))
                .count();
        return Math.toIntExact(baseByKey.size() + targetOnlyCount);
    }

    private List<SnapshotRelationDiff> compareRelations(
            List<RelationRecord> baseRelations,
            List<RelationRecord> targetRelations,
            Map<String, SymbolRecord> baseByKey,
            Map<String, SymbolRecord> targetByKey
    ) {
        Map<RelationKey, RelationRecord> baseRelationByKey = toRelationMap(baseRelations);
        Map<RelationKey, RelationRecord> targetRelationByKey = toRelationMap(targetRelations);
        List<SnapshotRelationDiff> diffs = new ArrayList<>();

        for (Map.Entry<RelationKey, RelationRecord> entry : targetRelationByKey.entrySet()) {
            if (!baseRelationByKey.containsKey(entry.getKey())) {
                diffs.add(SnapshotRelationDiff.from(
                        "added",
                        "Relation exists only in the target snapshot.",
                        entry.getValue(),
                        targetByKey
                ));
            }
        }

        for (Map.Entry<RelationKey, RelationRecord> entry : baseRelationByKey.entrySet()) {
            if (!targetRelationByKey.containsKey(entry.getKey())) {
                diffs.add(SnapshotRelationDiff.from(
                        "deleted",
                        "Relation exists only in the base snapshot.",
                        entry.getValue(),
                        baseByKey
                ));
            }
        }

        return diffs.stream()
                .sorted(Comparator.comparing(SnapshotRelationDiff::changeType)
                        .thenComparing(SnapshotRelationDiff::relationType)
                        .thenComparing(SnapshotRelationDiff::sourceQualifiedName)
                        .thenComparing(SnapshotRelationDiff::targetQualifiedName))
                .toList();
    }

    private Map<RelationKey, RelationRecord> toRelationMap(List<RelationRecord> relations) {
        return relations.stream()
                .filter(relation -> COMPARABLE_RELATION_TYPES.contains(relation.relationType()))
                .collect(Collectors.toMap(
                        relation -> new RelationKey(
                                relation.sourceSymbolKey(),
                                relation.targetSymbolKey(),
                                relation.relationType()
                        ),
                        relation -> relation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private SnapshotCompareRelationSummary summarizeRelations(
            List<RelationRecord> baseRelations,
            List<RelationRecord> targetRelations,
            List<SnapshotRelationDiff> diffs
    ) {
        int baseRelationCount = countComparableRelations(baseRelations);
        int targetRelationCount = countComparableRelations(targetRelations);
        int totalComparedRelations = countDistinctRelationKeys(baseRelations, targetRelations);
        int added = (int) diffs.stream().filter(diff -> diff.changeType().equals("added")).count();
        int deleted = (int) diffs.stream().filter(diff -> diff.changeType().equals("deleted")).count();
        int unchanged = Math.max(0, totalComparedRelations - added - deleted);
        return new SnapshotCompareRelationSummary(
                baseRelationCount,
                targetRelationCount,
                totalComparedRelations,
                added,
                deleted,
                unchanged,
                diffs.size()
        );
    }

    private int countComparableRelations(List<RelationRecord> relations) {
        return toRelationMap(relations).size();
    }

    private int countDistinctRelationKeys(List<RelationRecord> baseRelations, List<RelationRecord> targetRelations) {
        Set<RelationKey> relationKeys = new HashSet<>(toRelationMap(baseRelations).keySet());
        relationKeys.addAll(toRelationMap(targetRelations).keySet());
        return relationKeys.size();
    }

    private String buildCompareNote(
            ProjectSnapshot baseSnapshot,
            ProjectSnapshot targetSnapshot,
            SnapshotCompareSummary summary,
            SnapshotCompareRelationSummary relationSummary
    ) {
        return "Compared " + baseSnapshot.displayName() + " to " + targetSnapshot.displayName()
                + " across " + summary.totalComparedSymbols() + " symbol key(s) and "
                + relationSummary.totalComparedRelations() + " structural relation(s).";
    }

    public record SnapshotCompareResult(
            String projectId,
            SnapshotRef baseSnapshot,
            SnapshotRef targetSnapshot,
            SnapshotCompareSummary summary,
            List<SnapshotSymbolDiff> changes,
            SnapshotCompareRelationSummary relationSummary,
            List<SnapshotRelationDiff> relationChanges,
            String note
    ) {
    }

    public record SnapshotRef(
            String id,
            String displayName,
            String gitCommit,
            String gitCommitMessage
    ) {
        private static SnapshotRef from(ProjectSnapshot snapshot) {
            return new SnapshotRef(
                    snapshot.id(),
                    snapshot.displayName(),
                    snapshot.gitCommit(),
                    snapshot.gitCommitMessage()
            );
        }
    }

    public record SnapshotCompareSummary(
            int baseSymbolCount,
            int targetSymbolCount,
            int totalComparedSymbols,
            int added,
            int deleted,
            int modifiedApi,
            int modifiedImpl,
            int unchanged,
            int changed
    ) {
    }

    public record SnapshotCompareRelationSummary(
            int baseRelationCount,
            int targetRelationCount,
            int totalComparedRelations,
            int added,
            int deleted,
            int unchanged,
            int changed
    ) {
    }

    public record SnapshotSymbolDiff(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String kind,
            String symbolType,
            String filePath,
            String changeType,
            String reason
    ) {
        private static SnapshotSymbolDiff from(String changeType, String reason, SymbolRecord symbol) {
            return new SnapshotSymbolDiff(
                    symbol.symbolKey(),
                    symbol.qualifiedName(),
                    symbol.displayName(),
                    symbol.kind().name().toLowerCase(),
                    symbol.symbolType().name().toLowerCase(),
                    symbol.filePath(),
                    changeType,
                    reason
            );
        }
    }

    public record SnapshotRelationDiff(
            String sourceSymbolKey,
            String sourceDisplayName,
            String sourceQualifiedName,
            String targetSymbolKey,
            String targetDisplayName,
            String targetQualifiedName,
            String relationType,
            String filePath,
            Integer sourceLine,
            String changeType,
            String reason
    ) {
        private static SnapshotRelationDiff from(
                String changeType,
                String reason,
                RelationRecord relation,
                Map<String, SymbolRecord> symbolsByKey
        ) {
            SymbolRecord sourceSymbol = symbolsByKey.get(relation.sourceSymbolKey());
            SymbolRecord targetSymbol = symbolsByKey.get(relation.targetSymbolKey());
            return new SnapshotRelationDiff(
                    relation.sourceSymbolKey(),
                    sourceSymbol == null ? relation.sourceSymbolKey() : sourceSymbol.displayName(),
                    sourceSymbol == null ? relation.sourceSymbolKey() : sourceSymbol.qualifiedName(),
                    relation.targetSymbolKey(),
                    targetSymbol == null ? relation.targetSymbolKey() : targetSymbol.displayName(),
                    targetSymbol == null ? relation.targetSymbolKey() : targetSymbol.qualifiedName(),
                    relation.relationType().name().toLowerCase(),
                    relation.filePath(),
                    relation.sourceLine(),
                    changeType,
                    reason
            );
        }
    }

    private record RelationKey(
            String sourceSymbolKey,
            String targetSymbolKey,
            RelationType relationType
    ) {
    }
}
