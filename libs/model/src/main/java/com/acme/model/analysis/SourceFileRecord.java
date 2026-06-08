package com.acme.model.analysis;

public record SourceFileRecord(
        String path,
        String moduleName,
        String packageName,
        String contentHash,
        String scope
) {
}
