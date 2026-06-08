package com.acme.graphreview.api;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank String name,
        @NotBlank String rootPath
) {
}
