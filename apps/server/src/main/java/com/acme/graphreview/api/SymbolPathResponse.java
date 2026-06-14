package com.acme.graphreview.api;

import com.acme.graphreview.application.ReviewQueryService.SymbolPathNode;
import com.acme.graphreview.application.ReviewQueryService.SymbolPathResult;
import com.acme.graphreview.application.ReviewQueryService.SymbolPathSegment;
import java.util.List;
import java.util.Locale;

public record SymbolPathResponse(
        String snapshotId,
        String sourceSymbolKey,
        String targetSymbolKey,
        int maxDepth,
        boolean found,
        List<SymbolPathNodeResponse> nodes,
        List<SymbolPathSegmentResponse> segments,
        String note
) {
    public static SymbolPathResponse from(SymbolPathResult result) {
        return new SymbolPathResponse(
                result.snapshotId(),
                result.sourceSymbolKey(),
                result.targetSymbolKey(),
                result.maxDepth(),
                result.found(),
                result.nodes().stream().map(SymbolPathNodeResponse::from).toList(),
                result.segments().stream().map(SymbolPathSegmentResponse::from).toList(),
                result.note()
        );
    }

    public record SymbolPathNodeResponse(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String kind,
            String status
    ) {
        private static SymbolPathNodeResponse from(SymbolPathNode node) {
            return new SymbolPathNodeResponse(
                    node.symbolKey(),
                    node.qualifiedName(),
                    node.displayName(),
                    node.kind(),
                    node.status()
            );
        }
    }

    public record SymbolPathSegmentResponse(
            String sourceSymbolKey,
            String targetSymbolKey,
            String relationType,
            String filePath,
            Integer sourceLine
    ) {
        private static SymbolPathSegmentResponse from(SymbolPathSegment segment) {
            return new SymbolPathSegmentResponse(
                    segment.sourceSymbolKey(),
                    segment.targetSymbolKey(),
                    segment.relationType().name().toLowerCase(Locale.ROOT),
                    segment.filePath(),
                    segment.sourceLine()
            );
        }
    }
}
