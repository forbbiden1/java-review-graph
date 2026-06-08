package com.acme.graphreview.application;

import com.acme.graphreview.domain.RegisteredProject;

public record ProjectImportResult(
        RegisteredProject project,
        boolean created
) {
}
