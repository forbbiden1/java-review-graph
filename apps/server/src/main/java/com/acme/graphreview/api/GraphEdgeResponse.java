package com.acme.graphreview.api;

import com.acme.model.graph.RelationRecord;

public record GraphEdgeResponse(
        String source,
        String target,
        String type,
        String confidence
) {
    public static GraphEdgeResponse from(RelationRecord relation) {
        return new GraphEdgeResponse(
                relation.sourceSymbolKey(),
                relation.targetSymbolKey(),
                relation.relationType().name().toLowerCase(),
                relation.confidence()
        );
    }

    public static GraphEdgeResponse fromDependency(RelationRecord relation) {
        return new GraphEdgeResponse(
                relation.targetSymbolKey(),
                relation.sourceSymbolKey(),
                relation.relationType().name().toLowerCase(),
                relation.confidence()
        );
    }
}
