package com.acme.graphreview.domain;

import java.time.Instant;

public record ProjectSnapshot(
        String id,
        String projectId,
        String baseSnapshotId,
        String triggerType,
        String gitCommit,
        String gitCommitMessage,
        String displayName,
        String status,
        Instant createdAt
) {
    public ProjectSnapshot {
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
    }
}
