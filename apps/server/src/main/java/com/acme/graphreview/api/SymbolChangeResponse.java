package com.acme.graphreview.api;

import com.acme.graphreview.domain.StoredSymbolChange;

public record SymbolChangeResponse(
        String symbolKey,
        String changeType,
        String reason
) {
    public static SymbolChangeResponse from(StoredSymbolChange change) {
        return new SymbolChangeResponse(change.symbolKey(), change.changeType(), change.reason());
    }
}
