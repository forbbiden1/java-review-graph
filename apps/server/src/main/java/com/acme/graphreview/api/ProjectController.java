package com.acme.graphreview.api;

import com.acme.graphreview.application.ProjectImportCommand;
import com.acme.graphreview.application.ProjectImportResult;
import com.acme.graphreview.application.ProjectIndexCommand;
import com.acme.graphreview.application.ProjectIndexService;
import com.acme.graphreview.application.ProjectService;
import com.acme.graphreview.application.ChangeSetReviewService;
import com.acme.graphreview.application.GraphOrderingService;
import com.acme.graphreview.application.GraphOrderingService.GraphNodeLayout;
import com.acme.graphreview.application.ReviewQueryService;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.SymbolRecord;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectIndexService projectIndexService;
    private final ChangeSetReviewService changeSetReviewService;
    private final ReviewQueryService reviewQueryService;
    private final GraphOrderingService graphOrderingService;

    public ProjectController(
            ProjectService projectService,
            ProjectIndexService projectIndexService,
            ChangeSetReviewService changeSetReviewService,
            ReviewQueryService reviewQueryService,
            GraphOrderingService graphOrderingService
    ) {
        this.projectService = projectService;
        this.projectIndexService = projectIndexService;
        this.changeSetReviewService = changeSetReviewService;
        this.reviewQueryService = reviewQueryService;
        this.graphOrderingService = graphOrderingService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        ProjectImportResult result = projectService.importProject(
                new ProjectImportCommand(request.name(), request.rootPath())
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ProjectResponse.from(result.project()));
    }

    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.listProjects().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable("projectId") String projectId) {
        return ProjectResponse.from(projectService.getProject(projectId));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable("projectId") String projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/index")
    public ProjectIndexResponse indexProject(
            @PathVariable("projectId") String projectId,
            @Valid @RequestBody ProjectIndexRequest request
    ) {
        return ProjectIndexResponse.from(projectIndexService.indexProject(
                projectId,
                new ProjectIndexCommand(request.mode(), request.changeSource(), request.changedFiles())
        ));
    }

    @PostMapping("/{projectId}/review/change-set")
    public ChangeSetReviewResponse reviewChangeSet(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) ChangeSetReviewRequest request
    ) {
        ChangeSetReviewRequest normalizedRequest = normalizeChangeSetReviewRequest(request);
        return ChangeSetReviewResponse.from(changeSetReviewService.reviewChangeSet(
                projectId,
                new com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewCommand(
                        normalizedRequest.snapshotId(),
                        normalizedRequest.changeSource(),
                        normalizedRequest.changedFiles() == null ? List.of() : normalizedRequest.changedFiles()
                )
        ));
    }

    @PostMapping("/{projectId}/review/change-set/markdown")
    public ChangeSetReviewMarkdownResponse exportChangeSetReviewMarkdown(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) ChangeSetReviewRequest request
    ) {
        ChangeSetReviewRequest normalizedRequest = normalizeChangeSetReviewRequest(request);
        return ChangeSetReviewMarkdownResponse.from(changeSetReviewService.exportMarkdownReport(
                projectId,
                new com.acme.graphreview.application.ChangeSetReviewService.ChangeSetReviewCommand(
                        normalizedRequest.snapshotId(),
                        normalizedRequest.changeSource(),
                        normalizedRequest.changedFiles() == null ? List.of() : normalizedRequest.changedFiles()
                )
        ));
    }

    @GetMapping("/{projectId}/snapshots")
    public List<ProjectSnapshotResponse> listSnapshots(@PathVariable("projectId") String projectId) {
        return projectIndexService.listSnapshots(projectId).stream()
                .map(ProjectSnapshotResponse::from)
                .toList();
    }

    @GetMapping("/{projectId}/snapshots/{snapshotId}/diagnostics")
    public ProjectSnapshotDiagnosticsResponse getSnapshotDiagnostics(
            @PathVariable("projectId") String projectId,
            @PathVariable("snapshotId") String snapshotId
    ) {
        return ProjectSnapshotDiagnosticsResponse.from(
                projectIndexService.getSnapshotDiagnostics(projectId, snapshotId)
        );
    }

    @PatchMapping("/{projectId}/snapshots/{snapshotId}")
    public ProjectSnapshotResponse renameSnapshot(
            @PathVariable("projectId") String projectId,
            @PathVariable("snapshotId") String snapshotId,
            @Valid @RequestBody ProjectSnapshotRenameRequest request
    ) {
        return ProjectSnapshotResponse.from(projectIndexService.renameSnapshot(
                projectId,
                snapshotId,
                request.displayName()
        ));
    }

    @DeleteMapping("/{projectId}/snapshots/{snapshotId}")
    public ResponseEntity<Void> deleteSnapshot(
            @PathVariable("projectId") String projectId,
            @PathVariable("snapshotId") String snapshotId
    ) {
        projectIndexService.deleteSnapshot(projectId, snapshotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/graph/classes")
    public ClassGraphResponse getClassGraph(
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "snapshotId", required = false) String snapshotId
    ) {
        String resolvedSnapshotId = reviewQueryService.resolveSnapshotId(projectId, snapshotId);
        List<SymbolRecord> nodes = reviewQueryService.getClassGraphNodes(projectId, resolvedSnapshotId);
        List<RelationRecord> edges = reviewQueryService.getClassGraphEdges(projectId, resolvedSnapshotId);
        Map<String, GraphNodeLayout> layoutByNodeId = graphOrderingService.orderNodes(nodes, edges);
        return new ClassGraphResponse(
                resolvedSnapshotId,
                nodes.stream()
                        .map(node -> GraphNodeResponse.from(node, layoutByNodeId.get(node.symbolKey())))
                        .toList(),
                edges.stream()
                        .map(GraphEdgeResponse::fromDependency)
                        .toList()
        );
    }

    @GetMapping("/{projectId}/changes")
    public List<SymbolChangeResponse> getChanges(
            @PathVariable("projectId") String projectId,
            @RequestParam(name = "snapshotId", required = false) String snapshotId
    ) {
        return reviewQueryService.getChanges(projectId, snapshotId).stream()
                .map(SymbolChangeResponse::from)
                .toList();
    }

    @GetMapping("/{projectId}/classes/{classId}/method-graph")
    public MethodGraphResponse getMethodGraph(
            @PathVariable("projectId") String projectId,
            @PathVariable("classId") String classId,
            @RequestParam(name = "snapshotId", required = false) String snapshotId
    ) {
        return buildMethodGraphResponse(projectId, classId, snapshotId);
    }

    @GetMapping("/{projectId}/method-graph")
    public MethodGraphResponse getMethodGraphByQuery(
            @PathVariable("projectId") String projectId,
            @RequestParam("classId") String classId,
            @RequestParam(name = "snapshotId", required = false) String snapshotId
    ) {
        return buildMethodGraphResponse(projectId, classId, snapshotId);
    }

    private MethodGraphResponse buildMethodGraphResponse(String projectId, String classId, String snapshotId) {
        String resolvedSnapshotId = reviewQueryService.resolveSnapshotId(projectId, snapshotId);
        List<SymbolRecord> nodes = reviewQueryService.getMethodGraphNodes(projectId, resolvedSnapshotId, classId);
        List<RelationRecord> edges = reviewQueryService.getMethodGraphEdges(projectId, resolvedSnapshotId, classId);
        Map<String, GraphNodeLayout> layoutByNodeId = graphOrderingService.orderNodes(nodes, edges);
        return new MethodGraphResponse(
                resolvedSnapshotId,
                classId,
                nodes.stream()
                        .map(node -> GraphNodeResponse.from(node, layoutByNodeId.get(node.symbolKey())))
                        .toList(),
                edges.stream()
                        .map(GraphEdgeResponse::from)
                        .toList()
        );
    }

    private ChangeSetReviewRequest normalizeChangeSetReviewRequest(ChangeSetReviewRequest request) {
        return request == null
                ? new ChangeSetReviewRequest(null, null, List.of())
                : request;
    }
}
