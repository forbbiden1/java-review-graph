package com.acme.graphreview.api;

import com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewMarkdownReport;

public record ChangeSetReviewMarkdownResponse(
        String fileName,
        String markdown
) {
    public static ChangeSetReviewMarkdownResponse from(ChangeSetReviewMarkdownReport report) {
        return new ChangeSetReviewMarkdownResponse(report.fileName(), report.markdown());
    }
}
