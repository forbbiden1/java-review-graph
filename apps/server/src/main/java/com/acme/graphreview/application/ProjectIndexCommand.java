package com.acme.graphreview.application;

import java.util.List;

public record ProjectIndexCommand(
        String mode,
        String changeSource,
        List<String> changedFiles
) {
}
