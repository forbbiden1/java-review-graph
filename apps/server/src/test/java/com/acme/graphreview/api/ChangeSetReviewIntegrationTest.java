package com.acme.graphreview.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:review-graph-change-set-test?mode=memory&cache=shared")
@AutoConfigureMockMvc
class ChangeSetReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reviewChangeSetReturnsChangedAndImpactedSymbolsForManualPaths() throws Exception {
        String projectId = "project-change-set-1";
        String snapshotId = "snapshot-change-set-1";
        String changedFileId = "file-change-set-service";
        String impactedFileId = "file-change-set-controller";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshot(projectId, snapshotId, now);
        insertSourceFile(projectId, snapshotId, changedFileId, "src/main/java/demo/Service.java", now);
        insertSourceFile(projectId, snapshotId, impactedFileId, "src/main/java/demo/Controller.java", now);
        insertSymbol(projectId, snapshotId, changedFileId, "type:demo.Service", "Service", "demo.Service", "modified_api", "CLASS");
        insertSymbol(projectId, snapshotId, impactedFileId, "type:demo.Controller", "Controller", "demo.Controller", "impacted", "CLASS");
        insertRelation(projectId, snapshotId, "relation-change-set-1", "type:demo.Service", "type:demo.Controller", "uses_type");
        insertSymbolChange(projectId, snapshotId, "change-impacted-1", "type:demo.Controller", "impacted");

        mockMvc.perform(post("/api/projects/{projectId}/review/change-set", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snapshotId": "snapshot-change-set-1",
                                  "changeSource": "manual",
                                  "changedFiles": [
                                    "src/main/java/demo/Service.java"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value(snapshotId))
                .andExpect(jsonPath("$.changedFiles[0]").value("src/main/java/demo/Service.java"))
                .andExpect(jsonPath("$.changedSymbols[0].symbolKey").value("type:demo.Service"))
                .andExpect(jsonPath("$.changedSymbols[0].reviewRole").value("changed"))
                .andExpect(jsonPath("$.impactedSymbols[0].symbolKey").value("type:demo.Controller"))
                .andExpect(jsonPath("$.impactedSymbols[0].reviewRole").value("impacted"))
                .andExpect(jsonPath("$.reviewTargets[0].symbolKey").value("type:demo.Service"))
                .andExpect(jsonPath("$.propagationPaths[0].fromSymbol.symbolKey").value("type:demo.Service"))
                .andExpect(jsonPath("$.propagationPaths[0].toSymbol.symbolKey").value("type:demo.Controller"))
                .andExpect(jsonPath("$.propagationPaths[0].relationType").value("uses_type"))
                .andExpect(jsonPath("$.testFocusSuggestions[0].symbol.symbolKey").value("type:demo.Service"))
                .andExpect(jsonPath("$.testFocusSuggestions[0].priority").value("high"))
                .andExpect(jsonPath("$.risk.level").value("medium"))
                .andExpect(jsonPath("$.risk.score").value(4))
                .andExpect(jsonPath("$.risk.reasons[0]").value("Public API or deleted symbol changed."))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Risk level: medium.")));
    }

    @Test
    void exportChangeSetReviewMarkdownReturnsMarkdownBody() throws Exception {
        String projectId = "project-change-set-2";
        String snapshotId = "snapshot-change-set-2";
        String changedFileId = "file-change-set-service-2";
        String impactedFileId = "file-change-set-controller-2";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshot(projectId, snapshotId, now);
        insertSourceFile(projectId, snapshotId, changedFileId, "src/main/java/demo/Service.java", now);
        insertSourceFile(projectId, snapshotId, impactedFileId, "src/main/java/demo/Controller.java", now);
        insertSymbol(projectId, snapshotId, changedFileId, "type:demo.ServiceExport", "Service", "demo.ServiceExport", "modified_api", "CLASS");
        insertSymbol(projectId, snapshotId, impactedFileId, "type:demo.ControllerExport", "Controller", "demo.ControllerExport", "impacted", "CLASS");
        insertRelation(projectId, snapshotId, "relation-change-set-2", "type:demo.ServiceExport", "type:demo.ControllerExport", "uses_type");
        insertSymbolChange(projectId, snapshotId, "change-impacted-2", "type:demo.ControllerExport", "impacted");

        mockMvc.perform(post("/api/projects/{projectId}/review/change-set/markdown", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snapshotId": "snapshot-change-set-2",
                                  "changeSource": "manual",
                                  "changedFiles": [
                                    "src/main/java/demo/Service.java"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("change-set-review-project-change-set-2-review-baseline-manual.md"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("# Change-Set Review Report")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("## Prioritized Review Targets")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("## Propagation Paths")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("## Test Focus Suggestions")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("Changed public API should be covered by direct contract tests.")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("`demo.ServiceExport` -> `demo.ControllerExport` via `uses_type`")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("Risk level: medium.")));
    }

    private void insertProject(String projectId, String now) {
        jdbcTemplate.update(
                """
                insert into project (id, name, root_path, build_tool, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                projectId,
                "change-set-project",
                "C:/repo/" + projectId,
                "maven",
                now,
                now
        );
    }

    private void insertSnapshot(String projectId, String snapshotId, String now) {
        jdbcTemplate.update(
                """
                insert into snapshot (
                    id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshotId,
                projectId,
                null,
                "manual",
                "abcdef1234567890",
                "Review baseline",
                "Review Baseline",
                "completed",
                now
        );
    }

    private void insertSourceFile(String projectId, String snapshotId, String fileId, String path, String now) {
        jdbcTemplate.update(
                """
                insert into source_file (id, project_id, snapshot_id, path, module_name, package_name, content_hash, scope, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                fileId,
                projectId,
                snapshotId,
                path,
                "demo",
                "demo",
                "content-hash-" + path,
                "main",
                now
        );
    }

    private void insertSymbol(
            String projectId,
            String snapshotId,
            String fileId,
            String symbolKey,
            String name,
            String qualifiedName,
            String changeStatus,
            String kind
    ) {
        jdbcTemplate.update(
                """
                insert into symbol (
                    id, project_id, snapshot_id, file_id, symbol_key, symbol_type, parent_symbol_key, name,
                    display_name, package_name, qualified_name, signature, kind, visibility, is_abstract,
                    is_static, start_line, end_line, api_hash, impl_hash, change_status, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "symbol-" + symbolKey,
                projectId,
                snapshotId,
                fileId,
                symbolKey,
                "type",
                null,
                name,
                name,
                "demo",
                qualifiedName,
                null,
                kind,
                "public",
                0,
                0,
                1,
                20,
                "api-hash-" + symbolKey,
                "impl-hash-" + symbolKey,
                changeStatus,
                null
        );
    }

    private void insertSymbolChange(String projectId, String snapshotId, String changeId, String symbolKey, String changeType) {
        jdbcTemplate.update(
                """
                insert into symbol_change (
                    id, project_id, snapshot_id, symbol_key, before_symbol_id, after_symbol_id, change_type, reason
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                changeId,
                projectId,
                snapshotId,
                symbolKey,
                null,
                symbolKey,
                changeType,
                "change-set-test"
        );
    }

    private void insertRelation(
            String projectId,
            String snapshotId,
            String relationId,
            String sourceSymbolKey,
            String targetSymbolKey,
            String relationType
    ) {
        jdbcTemplate.update(
                """
                insert into relation (
                    id, project_id, snapshot_id, source_symbol_key, target_symbol_key, relation_type,
                    confidence, source_file_id, source_line, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                relationId,
                projectId,
                snapshotId,
                sourceSymbolKey,
                targetSymbolKey,
                relationType,
                "exact",
                null,
                6,
                null
        );
    }
}
