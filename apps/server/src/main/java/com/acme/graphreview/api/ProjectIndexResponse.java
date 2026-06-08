package com.acme.graphreview.api;

import com.acme.graphreview.application.ProjectIndexResult;

public record ProjectIndexResponse(
        ProjectResponse project,
        ProjectSnapshotResponse snapshot,
        int typeCount,
        int methodCount,
        int relationCount,
        String note
) {
    public static ProjectIndexResponse from(ProjectIndexResult result) {
        return new ProjectIndexResponse(
                ProjectResponse.from(result.project()),
                ProjectSnapshotResponse.from(result.snapshot()),
                result.analysisSnapshot().typeCount(),
                result.analysisSnapshot().methodCount(),
                result.analysisSnapshot().relationCount(),
                result.analysisSnapshot().note()
        );
    }
}
