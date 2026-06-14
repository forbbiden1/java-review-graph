package com.acme.graphreview.api;

import java.util.List;

public record ChangeSetReviewRequest(
        String snapshotId,
        String changeSource,
        List<String> changedFiles
) {
}
