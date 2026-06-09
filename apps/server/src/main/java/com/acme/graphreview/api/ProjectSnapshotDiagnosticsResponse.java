package com.acme.graphreview.api;

import com.acme.graphreview.domain.ProjectSnapshot;
import java.time.Instant;
import java.util.List;

public record ProjectSnapshotDiagnosticsResponse(
        String id,
        String projectId,
        String baseSnapshotId,
        String triggerType,
        String gitCommit,
        String gitCommitMessage,
        String displayName,
        String status,
        Instant createdAt,
        String requestedMode,
        String effectiveMode,
        String changeSource,
        boolean includesWorkspaceChanges,
        String note,
        String fallbackReason,
        List<String> changedFiles,
        List<String> renamedPaths,
        List<String> rebuildPaths,
        List<String> removedPaths
) {
    public static ProjectSnapshotDiagnosticsResponse from(ProjectSnapshot snapshot) {
        return new ProjectSnapshotDiagnosticsResponse(
                snapshot.id(),
                snapshot.projectId(),
                snapshot.baseSnapshotId(),
                snapshot.triggerType(),
                snapshot.gitCommit(),
                snapshot.gitCommitMessage(),
                snapshot.displayName(),
                snapshot.status(),
                snapshot.createdAt(),
                snapshot.requestedMode(),
                snapshot.effectiveMode(),
                snapshot.changeSource(),
                snapshot.includesWorkspaceChanges(),
                snapshot.diagnosticsNote(),
                snapshot.fallbackReason(),
                snapshot.changedFiles(),
                snapshot.renamedPaths(),
                snapshot.rebuildPaths(),
                snapshot.removedPaths()
        );
    }
}
