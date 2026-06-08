package com.acme.graphreview.application;

public record ProjectImportCommand(
        String name,
        String rootPath
) {
}
