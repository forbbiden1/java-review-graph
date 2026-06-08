package com.acme.graphreview.domain;

import java.util.List;

public record ProjectOverview(
        String name,
        String purpose,
        List<ModuleSummary> modules
) {
}
