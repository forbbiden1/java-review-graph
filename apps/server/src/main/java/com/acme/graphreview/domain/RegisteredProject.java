package com.acme.graphreview.domain;

import java.time.Instant;

public record RegisteredProject(
        String id,
        String name,
        String rootPath,
        String buildTool,
        Instant createdAt,
        Instant updatedAt
) {
}
