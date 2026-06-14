package com.acme.graphreview.application;

import com.acme.graphreview.domain.StoredSymbolChange;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.review.ChangeStatus;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ChangeStatusCalculator {

    public List<SymbolRecord> applyChangeStatus(
            List<SymbolRecord> currentSymbols,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> currentRelations,
            List<RelationRecord> previousRelations,
            boolean incremental,
            int impactDepth
    ) {
        Map<String, SymbolRecord> previousBySymbolKey = previousSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Map<String, SymbolRecord> currentBySymbolKey = currentSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
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
                .collect(Collectors.toCollection(HashSet::new));
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
                previousRelations,
                impactDepth
        );

        return symbolsWithDirectStatus.stream()
                .map(symbol -> impactedSymbolKeys.contains(symbol.symbolKey()) && symbol.changeStatus() == ChangeStatus.UNCHANGED
                        ? copyWithStatus(symbol, ChangeStatus.IMPACTED)
                        : symbol)
                .toList();
    }

    public List<StoredSymbolChange> buildSymbolChanges(
            String projectId,
            String snapshotId,
            List<SymbolRecord> previousSymbols,
            List<SymbolRecord> currentSymbols
    ) {
        Map<String, SymbolRecord> previousBySymbolKey = previousSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Map<String, SymbolRecord> currentBySymbolKey = currentSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));

        List<StoredSymbolChange> changes = new ArrayList<>();
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
                        case IMPACTED -> "Symbol is related to a changed neighbor in the current snapshot impact propagation.";
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
            List<RelationRecord> previousRelations,
            int impactDepth
    ) {
        Map<String, SymbolRecord> currentBySymbolKey = currentSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));
        Map<String, SymbolRecord> previousBySymbolKey = previousSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));

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

        int boundedDepth = Math.max(1, impactDepth);
        Set<String> impactedSymbolKeys = new HashSet<>();
        for (String changedSymbolKey : changedSymbolKeys) {
            SymbolRecord changedSymbol = currentBySymbolKey.get(changedSymbolKey);
            if (changedSymbol == null) {
                changedSymbol = previousBySymbolKey.get(changedSymbolKey);
            }
            if (changedSymbol == null) {
                continue;
            }

            ArrayDeque<ImpactTraversalState> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.addLast(new ImpactTraversalState(changedSymbolKey, 0));
            visited.add(changedSymbolKey);

            while (!queue.isEmpty()) {
                ImpactTraversalState state = queue.removeFirst();
                if (state.depth() >= boundedDepth) {
                    continue;
                }
                for (String neighborKey : neighborsBySymbolKey.getOrDefault(state.symbolKey(), Set.of())) {
                    if (!visited.add(neighborKey)) {
                        continue;
                    }
                    SymbolRecord neighbor = currentBySymbolKey.get(neighborKey);
                    if (neighbor == null) {
                        continue;
                    }
                    if (neighbor.symbolType() == changedSymbol.symbolType()
                            || neighbor.symbolType() == com.acme.model.graph.SymbolType.TYPE
                            || changedSymbol.symbolType() == com.acme.model.graph.SymbolType.TYPE) {
                        impactedSymbolKeys.add(neighborKey);
                    }
                    queue.addLast(new ImpactTraversalState(neighborKey, state.depth() + 1));
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

    private record ImpactTraversalState(String symbolKey, int depth) {
    }
}
