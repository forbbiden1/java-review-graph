package com.acme.graphreview.api;

import com.acme.model.graph.SymbolRecord;
import com.acme.graphreview.application.GraphOrderingService.GraphNodeLayout;

public record GraphNodeResponse(
        String id,
        String name,
        String qualifiedName,
        String kind,
        String status,
        int layer,
        int order,
        String group,
        int groupOrder,
        String placement
) {
    public static GraphNodeResponse from(SymbolRecord symbol, GraphNodeLayout layout) {
        return new GraphNodeResponse(
                symbol.symbolKey(),
                symbol.displayName(),
                symbol.qualifiedName(),
                symbol.kind().name().toLowerCase(),
                symbol.changeStatus().name().toLowerCase(),
                layout.layer(),
                layout.order(),
                layout.group(),
                layout.groupOrder(),
                layout.placement()
        );
    }
}
