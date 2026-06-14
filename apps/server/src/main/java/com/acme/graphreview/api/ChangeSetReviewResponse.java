package com.acme.graphreview.api;

import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewResult;
import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetRiskFactor;
import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewSymbol;
import com.acme.graphreview.application.ChangeSetReviewService.PropagationPath;
import com.acme.graphreview.application.ChangeSetReviewService.TestFocusSuggestion;
import java.util.List;

public record ChangeSetReviewResponse(
        String projectId,
        String snapshotId,
        String snapshotDisplayName,
        String note,
        List<String> changedFiles,
        List<String> renamedPaths,
        boolean includesWorkspaceChanges,
        List<ChangeSetReviewSymbolResponse> changedSymbols,
        List<ChangeSetReviewSymbolResponse> impactedSymbols,
        List<ChangeSetReviewSymbolResponse> reviewTargets,
        List<PropagationPathResponse> propagationPaths,
        List<TestFocusSuggestionResponse> testFocusSuggestions,
        RiskResponse risk,
        String summary
) {
    public static ChangeSetReviewResponse from(ChangeSetReviewResult result) {
        return new ChangeSetReviewResponse(
                result.projectId(),
                result.snapshotId(),
                result.snapshotDisplayName(),
                result.note(),
                result.changedFiles(),
                result.renamedPaths(),
                result.includesWorkspaceChanges(),
                result.changedSymbols().stream().map(ChangeSetReviewSymbolResponse::from).toList(),
                result.impactedSymbols().stream().map(ChangeSetReviewSymbolResponse::from).toList(),
                result.reviewTargets().stream().map(ChangeSetReviewSymbolResponse::from).toList(),
                result.propagationPaths().stream().map(PropagationPathResponse::from).toList(),
                result.testFocusSuggestions().stream().map(TestFocusSuggestionResponse::from).toList(),
                RiskResponse.from(result.risk()),
                result.summary()
        );
    }

    public record RiskResponse(
            String level,
            int score,
            List<String> reasons,
            List<RiskFactorResponse> factors
    ) {
        private static RiskResponse from(com.acme.graphreview.application.ChangeSetReviewService.ChangeSetRiskSummary risk) {
            return new RiskResponse(
                    risk.riskLevel(),
                    risk.riskScore(),
                    risk.reasons(),
                    risk.factors().stream().map(RiskFactorResponse::from).toList()
            );
        }
    }

    public record RiskFactorResponse(
            String code,
            String summary,
            int score,
            String severity,
            List<String> evidence
    ) {
        private static RiskFactorResponse from(ChangeSetRiskFactor factor) {
            return new RiskFactorResponse(
                    factor.code(),
                    factor.summary(),
                    factor.score(),
                    factor.severity(),
                    factor.evidence()
            );
        }
    }

    public record ChangeSetReviewSymbolResponse(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String kind,
            String status,
            String reviewRole
    ) {
        private static ChangeSetReviewSymbolResponse from(ChangeSetReviewSymbol symbol) {
            return new ChangeSetReviewSymbolResponse(
                    symbol.symbolKey(),
                    symbol.qualifiedName(),
                    symbol.displayName(),
                    symbol.kind(),
                    symbol.status(),
                    symbol.reviewRole()
            );
        }
    }

    public record PropagationPathResponse(
            ChangeSetReviewSymbolResponse fromSymbol,
            ChangeSetReviewSymbolResponse toSymbol,
            String relationType,
            String filePath,
            Integer sourceLine
    ) {
        private static PropagationPathResponse from(PropagationPath path) {
            return new PropagationPathResponse(
                    ChangeSetReviewSymbolResponse.from(path.fromSymbol()),
                    ChangeSetReviewSymbolResponse.from(path.toSymbol()),
                    path.relationType().name().toLowerCase(),
                    path.filePath(),
                    path.sourceLine()
            );
        }
    }

    public record TestFocusSuggestionResponse(
            ChangeSetReviewSymbolResponse symbol,
            String priority,
            String reason
    ) {
        private static TestFocusSuggestionResponse from(TestFocusSuggestion suggestion) {
            return new TestFocusSuggestionResponse(
                    ChangeSetReviewSymbolResponse.from(suggestion.symbol()),
                    suggestion.priority(),
                    suggestion.reason()
            );
        }
    }
}
