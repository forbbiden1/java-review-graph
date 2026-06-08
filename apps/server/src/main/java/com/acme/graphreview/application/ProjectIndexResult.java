package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import com.acme.graphreview.domain.RegisteredProject;
import com.acme.model.analysis.AnalysisSnapshot;

public record ProjectIndexResult(
        RegisteredProject project,
        ProjectSnapshot snapshot,
        AnalysisSnapshot analysisSnapshot
) {
}
