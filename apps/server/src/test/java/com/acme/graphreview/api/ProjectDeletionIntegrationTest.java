package com.acme.graphreview.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:review-graph-delete-test?mode=memory&cache=shared")
@AutoConfigureMockMvc
class ProjectDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deleteProjectRemovesProjectAndAssociatedReviewData() throws Exception {
        String projectId = "project-delete-1";
        String snapshotId = "snapshot-delete-1";
        String fileId = "file-delete-1";
        String symbolId = "symbol-delete-1";
        String relationId = "relation-delete-1";
        String changeId = "change-delete-1";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshot(projectId, snapshotId, now);
        insertSourceFile(projectId, snapshotId, fileId, now);
        insertSymbol(projectId, snapshotId, fileId, symbolId);
        insertRelation(projectId, snapshotId, relationId);
        insertSymbolChange(projectId, snapshotId, changeId);

        mockMvc.perform(delete("/api/projects/{projectId}", projectId))
                .andExpect(status().isNoContent());

        assertEquals(0, countRows("project", "id", projectId));
        assertEquals(0, countRows("snapshot", "project_id", projectId));
        assertEquals(0, countRows("source_file", "project_id", projectId));
        assertEquals(0, countRows("symbol", "project_id", projectId));
        assertEquals(0, countRows("relation", "project_id", projectId));
        assertEquals(0, countRows("symbol_change", "project_id", projectId));
    }

    private void insertProject(String projectId, String now) {
        jdbcTemplate.update(
                """
                insert into project (id, name, root_path, build_tool, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                projectId,
                "delete-project",
                "C:/repo/delete-project",
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
                null,
                null,
                snapshotId,
                "completed",
                now
        );
    }

    private void insertSourceFile(String projectId, String snapshotId, String fileId, String now) {
        jdbcTemplate.update(
                """
                insert into source_file (id, project_id, snapshot_id, path, module_name, package_name, content_hash, scope, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                fileId,
                projectId,
                snapshotId,
                "src/main/java/demo/DeleteMe.java",
                "demo",
                "demo",
                "content-hash",
                "main",
                now
        );
    }

    private void insertSymbol(String projectId, String snapshotId, String fileId, String symbolId) {
        jdbcTemplate.update(
                """
                insert into symbol (
                    id, project_id, snapshot_id, file_id, symbol_key, symbol_type, parent_symbol_key, name,
                    display_name, package_name, qualified_name, signature, kind, visibility, is_abstract,
                    is_static, start_line, end_line, api_hash, impl_hash, change_status, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                symbolId,
                projectId,
                snapshotId,
                fileId,
                "demo/DeleteMe",
                "type",
                null,
                "DeleteMe",
                "DeleteMe",
                "demo",
                "demo.DeleteMe",
                null,
                "CLASS",
                "public",
                0,
                0,
                1,
                10,
                "api-hash",
                "impl-hash",
                "unchanged",
                null
        );
    }

    private void insertRelation(String projectId, String snapshotId, String relationId) {
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
                "demo/DeleteMe",
                "demo/Target",
                "uses_type",
                "high",
                null,
                null,
                null
        );
    }

    private void insertSymbolChange(String projectId, String snapshotId, String changeId) {
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
                "demo/DeleteMe",
                null,
                "demo/DeleteMe",
                "added",
                "delete-test"
        );
    }

    private int countRows(String tableName, String columnName, String projectId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                projectId
        );
    }
}
