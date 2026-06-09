package com.acme.graphreview.domain;

import java.time.Instant;
import java.util.List;

public record ProjectSnapshot(
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
        String diagnosticsNote,
        String fallbackReason,
        List<String> changedFiles,
        List<String> renamedPaths,
        List<String> rebuildPaths,
        List<String> removedPaths
) {
    public ProjectSnapshot {
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        requestedMode = normalizeOptionalText(requestedMode);
        effectiveMode = normalizeOptionalText(effectiveMode);
        changeSource = normalizeOptionalText(changeSource);
        diagnosticsNote = normalizeOptionalText(diagnosticsNote);
        fallbackReason = normalizeOptionalText(fallbackReason);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        renamedPaths = renamedPaths == null ? List.of() : List.copyOf(renamedPaths);
        rebuildPaths = rebuildPaths == null ? List.of() : List.copyOf(rebuildPaths);
        removedPaths = removedPaths == null ? List.of() : List.copyOf(removedPaths);
    }

    public ProjectSnapshot(
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
        this(
                id,
                projectId,
                baseSnapshotId,
                triggerType,
                gitCommit,
                gitCommitMessage,
                displayName,
                status,
                createdAt,
                null,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
