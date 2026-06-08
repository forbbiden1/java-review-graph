package com.acme.graphreview.infrastructure;

public class SnapshotNotFoundException extends RuntimeException {

    public SnapshotNotFoundException(String projectId) {
        super("No snapshot found for project: " + projectId);
    }
}
