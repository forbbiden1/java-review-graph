package com.acme.graphreview.api;

import java.util.List;

public record MethodGraphResponse(
        String snapshotId,
        String classId,
        List<GraphNodeResponse> nodes,
        List<GraphEdgeResponse> edges
) {
}
