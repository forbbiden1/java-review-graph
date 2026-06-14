package com.acme.graphreview.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:review-graph-snapshot-compare-test?mode=memory&cache=shared")
@AutoConfigureMockMvc
class SnapshotCompareIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void comparesTwoSnapshotsAndReturnsSymbolDiffSummary() throws Exception {
        String projectId = "project-snapshot-compare-1";
        String baseSnapshotId = "snapshot-base-1";
        String targetSnapshotId = "snapshot-target-1";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshot(projectId, baseSnapshotId, "Baseline", now);
        insertSnapshot(projectId, targetSnapshotId, "Target", now);

        insertSourceFile(projectId, baseSnapshotId, "file-base-service", "src/main/java/demo/Service.java", now);
        insertSourceFile(projectId, baseSnapshotId, "file-base-controller", "src/main/java/demo/Controller.java", now);
        insertSourceFile(projectId, targetSnapshotId, "file-target-service", "src/main/java/demo/Service.java", now);
        insertSourceFile(projectId, targetSnapshotId, "file-target-repo", "src/main/java/demo/Repository.java", now);

        insertSymbol(projectId, baseSnapshotId, "file-base-service", "type:demo.Service", "Service", "demo.Service", "api-service-v1", "impl-service-v1");
        insertSymbol(projectId, baseSnapshotId, "file-base-controller", "type:demo.Controller", "Controller", "demo.Controller", "api-controller-v1", "impl-controller-v1");

        insertSymbol(projectId, targetSnapshotId, "file-target-service", "type:demo.Service", "Service", "demo.Service", "api-service-v2", "impl-service-v2");
        insertSymbol(projectId, targetSnapshotId, "file-target-repo", "type:demo.Repository", "Repository", "demo.Repository", "api-repository-v1", "impl-repository-v1");

        insertRelation(projectId, baseSnapshotId, "type:demo.Service", "type:demo.Controller", "uses_type", "file-base-service", 8);
        insertRelation(projectId, baseSnapshotId, "type:demo.Controller", "type:demo.Service", "uses_type", "file-base-controller", 12);
        insertRelation(projectId, baseSnapshotId, "type:demo.Service", "method:demo.Service.save()", "declares", "file-base-service", 3);
        insertRelation(projectId, targetSnapshotId, "type:demo.Service", "type:demo.Controller", "uses_type", "file-target-service", 8);
        insertRelation(projectId, targetSnapshotId, "type:demo.Service", "type:demo.Repository", "uses_type", "file-target-service", 14);
        insertRelation(projectId, targetSnapshotId, "type:demo.Repository", "method:demo.Repository.save()", "declares", "file-target-repo", 3);

        mockMvc.perform(get("/api/projects/{projectId}/snapshots/compare", projectId)
                        .param("baseSnapshotId", baseSnapshotId)
                        .param("targetSnapshotId", targetSnapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSnapshot.id").value(baseSnapshotId))
                .andExpect(jsonPath("$.targetSnapshot.id").value(targetSnapshotId))
                .andExpect(jsonPath("$.summary.baseSymbolCount").value(2))
                .andExpect(jsonPath("$.summary.targetSymbolCount").value(2))
                .andExpect(jsonPath("$.summary.totalComparedSymbols").value(3))
                .andExpect(jsonPath("$.summary.added").value(1))
                .andExpect(jsonPath("$.summary.deleted").value(1))
                .andExpect(jsonPath("$.summary.modifiedApi").value(1))
                .andExpect(jsonPath("$.summary.modifiedImpl").value(0))
                .andExpect(jsonPath("$.summary.unchanged").value(0))
                .andExpect(jsonPath("$.summary.changed").value(3))
                .andExpect(jsonPath("$.changes[?(@.symbolKey=='type:demo.Repository')].changeType").value("added"))
                .andExpect(jsonPath("$.changes[?(@.symbolKey=='type:demo.Controller')].changeType").value("deleted"))
                .andExpect(jsonPath("$.changes[?(@.symbolKey=='type:demo.Service')].changeType").value("modified_api"))
                .andExpect(jsonPath("$.relationSummary.baseRelationCount").value(2))
                .andExpect(jsonPath("$.relationSummary.targetRelationCount").value(2))
                .andExpect(jsonPath("$.relationSummary.totalComparedRelations").value(3))
                .andExpect(jsonPath("$.relationSummary.added").value(1))
                .andExpect(jsonPath("$.relationSummary.deleted").value(1))
                .andExpect(jsonPath("$.relationSummary.unchanged").value(1))
                .andExpect(jsonPath("$.relationSummary.changed").value(2))
                .andExpect(jsonPath("$.relationChanges[?(@.targetSymbolKey=='type:demo.Repository')].changeType").value("added"))
                .andExpect(jsonPath("$.relationChanges[?(@.sourceSymbolKey=='type:demo.Controller')].changeType").value("deleted"))
                .andExpect(jsonPath("$.relationChanges[?(@.relationType=='declares')]").doesNotExist());
    }

    private void insertProject(String projectId, String now) {
        jdbcTemplate.update(
                """
                insert into project (id, name, root_path, build_tool, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                projectId,
                "snapshot-compare-project",
                "C:/repo/" + projectId,
                "maven",
                now,
                now
        );
    }

    private void insertSnapshot(String projectId, String snapshotId, String displayName, String now) {
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
                displayName,
                displayName,
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
            String apiHash,
            String implHash
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
                "symbol-" + snapshotId + "-" + symbolKey,
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
                "CLASS",
                "public",
                0,
                0,
                1,
                20,
                apiHash,
                implHash,
                "unchanged",
                null
        );
    }

    private void insertRelation(
            String projectId,
            String snapshotId,
            String sourceSymbolKey,
            String targetSymbolKey,
            String relationType,
            String fileId,
            int sourceLine
    ) {
        jdbcTemplate.update(
                """
                insert into relation (
                    id, project_id, snapshot_id, source_symbol_key, target_symbol_key, relation_type,
                    confidence, source_file_id, source_line, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "relation-" + snapshotId + "-" + sourceSymbolKey + "-" + targetSymbolKey + "-" + relationType,
                projectId,
                snapshotId,
                sourceSymbolKey,
                targetSymbolKey,
                relationType,
                "exact",
                fileId,
                sourceLine,
                null
        );
    }
}
