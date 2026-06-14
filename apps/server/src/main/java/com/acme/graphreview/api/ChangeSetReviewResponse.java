package com.acme.graphreview.api;

import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewResult;
import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewSymbol;
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
                result.summary()
        );
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
}
