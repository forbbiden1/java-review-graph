package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.SnapshotNotFoundException;
import com.acme.model.graph.SymbolRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SnapshotCompareService {

    private final ProjectService projectService;
    private final SnapshotRepository snapshotRepository;
    private final SymbolRepository symbolRepository;

    public SnapshotCompareService(
            ProjectService projectService,
            SnapshotRepository snapshotRepository,
            SymbolRepository symbolRepository
    ) {
        this.projectService = projectService;
        this.snapshotRepository = snapshotRepository;
        this.symbolRepository = symbolRepository;
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

        Map<String, SymbolRecord> baseByKey = toSymbolMap(baseSymbols);
        Map<String, SymbolRecord> targetByKey = toSymbolMap(targetSymbols);
        List<SnapshotSymbolDiff> diffs = new ArrayList<>();

        for (SymbolRecord targetSymbol : targetSymbols) {
            SymbolRecord baseSymbol = baseByKey.get(targetSymbol.symbolKey());
            if (baseSymbol == null) {
                diffs.add(SnapshotSymbolDiff.from("added", "Symbol exists only in the target snapshot.", targetSymbol));
                continue;
            }
            if (!baseSymbol.apiHash().equals(targetSymbol.apiHash())) {
                diffs.add(SnapshotSymbolDiff.from("modified_api", "API hash differs between snapshots.", targetSymbol));
                continue;
            }
            if (!baseSymbol.implHash().equals(targetSymbol.implHash())) {
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

        return new SnapshotCompareResult(
                projectId,
                SnapshotRef.from(baseSnapshot),
                SnapshotRef.from(targetSnapshot),
                summary,
                sortedDiffs,
                "Compared " + baseSnapshot.displayName() + " to " + targetSnapshot.displayName()
                        + " across " + summary.totalComparedSymbols() + " symbol key(s)."
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

    public record SnapshotCompareResult(
            String projectId,
            SnapshotRef baseSnapshot,
            SnapshotRef targetSnapshot,
            SnapshotCompareSummary summary,
            List<SnapshotSymbolDiff> changes,
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
}
