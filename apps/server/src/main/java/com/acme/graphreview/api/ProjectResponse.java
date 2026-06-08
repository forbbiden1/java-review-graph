package com.acme.graphreview.api;

import com.acme.graphreview.domain.RegisteredProject;
import java.time.Instant;

public record ProjectResponse(
        String id,
        String name,
        String rootPath,
        String buildTool,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(RegisteredProject project) {
        return new ProjectResponse(
                project.id(),
                project.name(),
                project.rootPath(),
                project.buildTool(),
                project.createdAt(),
                project.updatedAt()
        );
    }
}
