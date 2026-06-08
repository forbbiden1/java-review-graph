package com.acme.graphreview.api;

import java.util.List;

public record ClassGraphResponse(
        String snapshotId,
        List<GraphNodeResponse> nodes,
        List<GraphEdgeResponse> edges
) {
}
