package com.acme.graphreview.api;

import jakarta.validation.constraints.NotBlank;

public record ProjectSnapshotRenameRequest(
        @NotBlank String displayName
) {
}
