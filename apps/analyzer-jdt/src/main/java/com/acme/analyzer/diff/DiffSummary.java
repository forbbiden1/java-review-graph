package com.acme.analyzer.diff;

public record DiffSummary(
        int addedSymbols,
        int deletedSymbols,
        int modifiedApiSymbols,
        int modifiedImplSymbols,
        int impactedSymbols
) {
}
