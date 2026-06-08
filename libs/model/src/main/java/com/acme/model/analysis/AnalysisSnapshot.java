package com.acme.model.analysis;

import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.SymbolRecord;
import java.time.Instant;
import java.util.List;

public record AnalysisSnapshot(
        String snapshotId,
        String projectId,
        Instant createdAt,
        List<SourceFileRecord> files,
        List<SymbolRecord> symbols,
        List<RelationRecord> relations,
        String note
) {
    public int typeCount() {
        return (int) symbols.stream()
                .filter(symbol -> symbol.symbolType() == com.acme.model.graph.SymbolType.TYPE)
                .count();
    }

    public int methodCount() {
        return (int) symbols.stream()
                .filter(symbol -> symbol.symbolType() == com.acme.model.graph.SymbolType.METHOD)
                .count();
    }

    public int relationCount() {
        return relations.size();
    }
}
