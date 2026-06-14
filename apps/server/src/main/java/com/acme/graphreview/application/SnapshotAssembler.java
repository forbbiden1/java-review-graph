package com.acme.graphreview.application;

import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.model.analysis.AnalysisSnapshot;
import com.acme.model.analysis.SourceFileRecord;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SnapshotAssembler {

    public AnalysisSnapshot mergeIncrementalSnapshot(
            String snapshotId,
            String projectId,
            AnalysisSnapshot partialSnapshot,
            List<StoredSourceFile> previousFiles,
            List<SymbolRecord> previousSymbols,
            List<RelationRecord> previousRelations,
            IncrementalPlanner.IncrementalPlan incrementalPlan
    ) {
        Set<String> replacedPaths = incrementalPlan.replacedPaths();
        Map<String, SymbolRecord> previousSymbolByKey = previousSymbols.stream()
                .collect(Collectors.toMap(SymbolRecord::symbolKey, symbol -> symbol, (left, right) -> left));

        List<SourceFileRecord> mergedFiles = new ArrayList<>();
        previousFiles.stream()
                .filter(file -> !replacedPaths.contains(file.path()))
                .map(this::toSourceFileRecord)
                .forEach(mergedFiles::add);
        mergedFiles.addAll(partialSnapshot.files());

        List<SymbolRecord> mergedSymbols = new ArrayList<>();
        previousSymbols.stream()
                .filter(symbol -> !replacedPaths.contains(symbol.filePath()))
                .forEach(mergedSymbols::add);
        mergedSymbols.addAll(partialSnapshot.symbols());

        Map<String, String> mergedTypeKeyByQualifiedName = mergedSymbols.stream()
                .filter(symbol -> symbol.symbolType() == SymbolType.TYPE)
                .collect(Collectors.toMap(SymbolRecord::qualifiedName, SymbolRecord::symbolKey, (left, right) -> right));
        Set<String> mergedSymbolKeys = mergedSymbols.stream()
                .map(SymbolRecord::symbolKey)
                .collect(Collectors.toCollection(HashSet::new));

        List<RelationRecord> mergedRelations = new ArrayList<>();
        for (RelationRecord previousRelation : previousRelations) {
            String sourceFilePath = relationSourcePath(previousRelation, previousSymbolByKey);
            if (sourceFilePath != null && replacedPaths.contains(sourceFilePath)) {
                continue;
            }
            if (!relationReferencesExistingSymbols(previousRelation, mergedSymbolKeys)) {
                continue;
            }
            mergedRelations.add(previousRelation);
        }
        partialSnapshot.relations().stream()
                .map(relation -> resolveIncrementalRelation(relation, mergedTypeKeyByQualifiedName))
                .filter(relation -> relationReferencesExistingSymbols(relation, mergedSymbolKeys))
                .forEach(mergedRelations::add);

        return new AnalysisSnapshot(
                snapshotId,
                projectId,
                partialSnapshot.createdAt(),
                deduplicateFiles(mergedFiles),
                deduplicateSymbols(mergedSymbols),
                deduplicateRelations(mergedRelations),
                incrementalPlan.noteOverride()
        );
    }

    public AnalysisSnapshot withNote(AnalysisSnapshot analysisSnapshot, String noteOverride) {
        if (noteOverride == null || noteOverride.isBlank()) {
            return analysisSnapshot;
        }
        return new AnalysisSnapshot(
                analysisSnapshot.snapshotId(),
                analysisSnapshot.projectId(),
                analysisSnapshot.createdAt(),
                analysisSnapshot.files(),
                analysisSnapshot.symbols(),
                analysisSnapshot.relations(),
                noteOverride
        );
    }

    public Map<String, String> buildFileIdByPath(List<StoredSourceFile> storedFiles) {
        Map<String, String> fileIdsByPath = new LinkedHashMap<>();
        for (StoredSourceFile storedFile : storedFiles) {
            fileIdsByPath.put(storedFile.path(), storedFile.id());
        }
        return fileIdsByPath;
    }

    private SourceFileRecord toSourceFileRecord(StoredSourceFile storedSourceFile) {
        return new SourceFileRecord(
                storedSourceFile.path(),
                storedSourceFile.moduleName(),
                storedSourceFile.packageName(),
                storedSourceFile.contentHash(),
                storedSourceFile.scope()
        );
    }

    private List<SourceFileRecord> deduplicateFiles(List<SourceFileRecord> files) {
        Map<String, SourceFileRecord> fileByPath = new LinkedHashMap<>();
        for (SourceFileRecord file : files) {
            fileByPath.put(file.path(), file);
        }
        return List.copyOf(fileByPath.values());
    }

    private List<SymbolRecord> deduplicateSymbols(List<SymbolRecord> symbols) {
        Map<String, SymbolRecord> symbolByKey = new LinkedHashMap<>();
        for (SymbolRecord symbol : symbols) {
            symbolByKey.put(symbol.symbolKey(), symbol);
        }
        return List.copyOf(symbolByKey.values());
    }

    private List<RelationRecord> deduplicateRelations(List<RelationRecord> relations) {
        Map<String, RelationRecord> uniqueRelations = new LinkedHashMap<>();
        for (RelationRecord relation : relations) {
            String key = relation.sourceSymbolKey() + "|" + relation.targetSymbolKey() + "|" + relation.relationType().name();
            uniqueRelations.putIfAbsent(key, relation);
        }
        return List.copyOf(uniqueRelations.values());
    }

    private RelationRecord resolveIncrementalRelation(RelationRecord relation, Map<String, String> mergedTypeKeyByQualifiedName) {
        if (!relation.targetSymbolKey().startsWith("external:type:")) {
            return relation;
        }

        String qualifiedName = relation.targetSymbolKey().substring("external:type:".length());
        String resolvedTarget = mergedTypeKeyByQualifiedName.get(qualifiedName);
        if (resolvedTarget == null) {
            return relation;
        }
        return new RelationRecord(
                relation.sourceSymbolKey(),
                resolvedTarget,
                relation.relationType(),
                relation.confidence(),
                relation.filePath(),
                relation.sourceLine()
        );
    }

    private boolean relationReferencesExistingSymbols(RelationRecord relation, Set<String> mergedSymbolKeys) {
        if (!mergedSymbolKeys.contains(relation.sourceSymbolKey())) {
            return false;
        }
        return relation.targetSymbolKey().startsWith("external:") || mergedSymbolKeys.contains(relation.targetSymbolKey());
    }

    private String relationSourcePath(RelationRecord relation, Map<String, SymbolRecord> symbolByKey) {
        if (relation.filePath() != null && !relation.filePath().isBlank()) {
            return relation.filePath();
        }
        SymbolRecord source = symbolByKey.get(relation.sourceSymbolKey());
        return source == null ? null : source.filePath();
    }
}
