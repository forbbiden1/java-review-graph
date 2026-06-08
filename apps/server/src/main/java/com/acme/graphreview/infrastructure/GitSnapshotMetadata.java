package com.acme.graphreview.infrastructure;

public record GitSnapshotMetadata(
        String gitCommit,
        String gitCommitMessage
) {
    public static GitSnapshotMetadata uncommitted() {
        return new GitSnapshotMetadata(null, null);
    }
}
