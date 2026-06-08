package com.acme.graphreview.infrastructure;

import com.acme.analyzer.parser.AnalysisRequest;
import com.acme.analyzer.parser.JdtAnalyzerEngine;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.model.analysis.AnalysisSnapshot;
import org.springframework.stereotype.Component;

@Component
public class JdtProjectAnalyzer {

    private final JdtAnalyzerEngine engine = new JdtAnalyzerEngine();

    public AnalysisSnapshot analyze(ProjectDescriptor descriptor, AnalysisRequest request) {
        return engine.run(descriptor, request);
    }
}
