package com.acme.analyzer.project;

import java.nio.file.Path;
import java.util.List;

public record ProjectDescriptor(
        String projectId,
        String buildTool,
        Path rootPath,
        List<Path> sourceRoots,
        List<Path> moduleRoots
) {
}
