package com.acme.graphreview.api;

import com.acme.graphreview.domain.ProjectSnapshot;
import java.time.Instant;

public record ProjectSnapshotResponse(
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
    public static ProjectSnapshotResponse from(ProjectSnapshot snapshot) {
        return new ProjectSnapshotResponse(
                snapshot.id(),
                snapshot.projectId(),
                snapshot.baseSnapshotId(),
                snapshot.triggerType(),
                snapshot.gitCommit(),
                snapshot.gitCommitMessage(),
                snapshot.displayName(),
                snapshot.status(),
                snapshot.createdAt()
        );
    }
}
