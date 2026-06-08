package com.acme.model.review;

public record ProjectGraphOverview(
        String projectName,
        int typeCount,
        int methodCount,
        int relationCount,
        String status
) {
}
