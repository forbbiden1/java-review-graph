package com.acme.graphreview.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ProjectIndexRequest(
        @NotBlank String mode,
        String changeSource,
        List<String> changedFiles
) {
}
