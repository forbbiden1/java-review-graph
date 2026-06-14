package com.acme.graphreview.api;

import com.acme.graphreview.application.SnapshotCompareService.SnapshotCompareResult;
import com.acme.graphreview.application.SnapshotCompareService.SnapshotCompareSummary;
import com.acme.graphreview.application.SnapshotCompareService.SnapshotRef;
import com.acme.graphreview.application.SnapshotCompareService.SnapshotSymbolDiff;
import java.util.List;

public record SnapshotCompareResponse(
        String projectId,
        SnapshotRefResponse baseSnapshot,
        SnapshotRefResponse targetSnapshot,
        SnapshotCompareSummaryResponse summary,
        List<SnapshotSymbolDiffResponse> changes,
        String note
) {
    public static SnapshotCompareResponse from(SnapshotCompareResult result) {
        return new SnapshotCompareResponse(
                result.projectId(),
                SnapshotRefResponse.from(result.baseSnapshot()),
                SnapshotRefResponse.from(result.targetSnapshot()),
                SnapshotCompareSummaryResponse.from(result.summary()),
                result.changes().stream().map(SnapshotSymbolDiffResponse::from).toList(),
                result.note()
        );
    }

    public record SnapshotRefResponse(
            String id,
            String displayName,
            String gitCommit,
            String gitCommitMessage
    ) {
        private static SnapshotRefResponse from(SnapshotRef snapshot) {
            return new SnapshotRefResponse(
                    snapshot.id(),
                    snapshot.displayName(),
                    snapshot.gitCommit(),
                    snapshot.gitCommitMessage()
            );
        }
    }

    public record SnapshotCompareSummaryResponse(
            int baseSymbolCount,
            int targetSymbolCount,
            int totalComparedSymbols,
            int added,
            int deleted,
            int modifiedApi,
            int modifiedImpl,
            int unchanged,
            int changed
    ) {
        private static SnapshotCompareSummaryResponse from(SnapshotCompareSummary summary) {
            return new SnapshotCompareSummaryResponse(
                    summary.baseSymbolCount(),
                    summary.targetSymbolCount(),
                    summary.totalComparedSymbols(),
                    summary.added(),
                    summary.deleted(),
                    summary.modifiedApi(),
                    summary.modifiedImpl(),
                    summary.unchanged(),
                    summary.changed()
            );
        }
    }

    public record SnapshotSymbolDiffResponse(
            String symbolKey,
            String qualifiedName,
            String displayName,
            String kind,
            String symbolType,
            String filePath,
            String changeType,
            String reason
    ) {
        private static SnapshotSymbolDiffResponse from(SnapshotSymbolDiff diff) {
            return new SnapshotSymbolDiffResponse(
                    diff.symbolKey(),
                    diff.qualifiedName(),
                    diff.displayName(),
                    diff.kind(),
                    diff.symbolType(),
                    diff.filePath(),
                    diff.changeType(),
                    diff.reason()
            );
        }
    }
}
