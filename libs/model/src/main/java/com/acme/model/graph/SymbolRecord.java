package com.acme.model.graph;

import com.acme.model.review.ChangeStatus;

public record SymbolRecord(
        String symbolKey,
        SymbolType symbolType,
        SymbolKind kind,
        String parentSymbolKey,
        String name,
        String packageName,
        String qualifiedName,
        String displayName,
        String signature,
        String filePath,
        int startLine,
        int endLine,
        String apiHash,
        String implHash,
        ChangeStatus changeStatus
) {
}
