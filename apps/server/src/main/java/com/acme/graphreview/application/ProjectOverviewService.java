package com.acme.graphreview.application;

import com.acme.graphreview.domain.ModuleSummary;
import com.acme.graphreview.domain.ProjectOverview;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProjectOverviewService {

    public ProjectOverview getOverview() {
        return new ProjectOverview(
                "java-review-graph",
                "Review-oriented Java code graph with incremental change highlighting.",
                List.of(
                        new ModuleSummary("libs/model", "Shared graph and review model", "libs/model"),
                        new ModuleSummary("apps/analyzer-jdt", "Java analysis pipeline", "apps/analyzer-jdt"),
                        new ModuleSummary("apps/server", "Spring Boot API", "apps/server"),
                        new ModuleSummary("apps/web", "Frontend review UI", "apps/web")
                )
        );
    }
}
