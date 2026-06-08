package com.acme.analyzer.parser;

import java.util.List;

public record AnalysisRequest(
        String snapshotId,
        boolean incremental,
        List<String> changedFiles
) {
}
