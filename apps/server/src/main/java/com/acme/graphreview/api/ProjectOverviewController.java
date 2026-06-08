package com.acme.graphreview.api;

import com.acme.graphreview.application.ProjectOverviewService;
import com.acme.graphreview.domain.ProjectOverview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectOverviewController {

    private final ProjectOverviewService projectOverviewService;

    public ProjectOverviewController(ProjectOverviewService projectOverviewService) {
        this.projectOverviewService = projectOverviewService;
    }

    @GetMapping("/bootstrap")
    public ProjectOverview bootstrap() {
        return projectOverviewService.getOverview();
    }
}
