package com.acme.graphreview.domain;

public record StoredSourceFile(
        String id,
        String path,
        String moduleName,
        String packageName,
        String contentHash,
        String scope
) {
}
