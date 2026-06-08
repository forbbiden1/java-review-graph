package com.acme.model.graph;

public record RelationRecord(
        String sourceSymbolKey,
        String targetSymbolKey,
        RelationType relationType,
        String confidence,
        String filePath,
        Integer sourceLine
) {
}
