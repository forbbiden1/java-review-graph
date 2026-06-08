package com.acme.graphreview.domain;

public record StoredSymbolChange(
        String id,
        String projectId,
        String snapshotId,
        String symbolKey,
        String beforeSymbolId,
        String afterSymbolId,
        String changeType,
        String reason
) {
}
