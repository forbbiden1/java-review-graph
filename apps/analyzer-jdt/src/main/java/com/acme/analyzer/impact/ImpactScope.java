package com.acme.analyzer.impact;

import java.util.List;

public record ImpactScope(
        String rootSymbolKey,
        List<String> impactedSymbolKeys
) {
}
