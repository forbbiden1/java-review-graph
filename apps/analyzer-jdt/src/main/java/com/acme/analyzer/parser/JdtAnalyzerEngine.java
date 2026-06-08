package com.acme.analyzer.parser;

import com.acme.analyzer.extractor.JavaAnalysisFacade;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.model.analysis.AnalysisSnapshot;

public class JdtAnalyzerEngine {

    private final JavaAnalysisFacade analysisFacade = new JavaAnalysisFacade();

    public AnalysisSnapshot run(ProjectDescriptor descriptor, AnalysisRequest request) {
        return analysisFacade.analyze(descriptor, request);
    }
}
