package com.acme.graphreview.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:review-graph-snapshot-test?mode=memory&cache=shared")
@AutoConfigureMockMvc
class SnapshotManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void renameSnapshotUpdatesDisplayNameButKeepsUuid() throws Exception {
        String projectId = "project-snapshot-rename";
        String snapshotId = "snapshot-rename-1";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshot(projectId, snapshotId, "Initial Name", now);

        mockMvc.perform(patch("/api/projects/{projectId}/snapshots/{snapshotId}", projectId, snapshotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Review Baseline"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(snapshotId))
                .andExpect(jsonPath("$.displayName").value("Review Baseline"));

        assertEquals(
                "Review Baseline",
                jdbcTemplate.queryForObject("select display_name from snapshot where id = ?", String.class, snapshotId)
        );
    }

    @Test
    void deleteSnapshotRemovesOnlySelectedSnapshotAndAssociatedReviewData() throws Exception {
        String projectId = "project-snapshot-delete";
        String snapshotId = "snapshot-delete-target";
        String siblingSnapshotId = "snapshot-delete-keep";
        String fileId = "file-delete-target";
        String symbolId = "symbol-delete-target";
        String relationId = "relation-delete-target";
        String changeId = "change-delete-target";
        String siblingFileId = "file-delete-keep";
        String siblingSymbolId = "symbol-delete-keep";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshot(projectId, snapshotId, "Delete Target", now);
        insertSnapshot(projectId, siblingSnapshotId, "Keep Snapshot", now);
        insertSourceFile(projectId, snapshotId, fileId, now);
        insertSourceFile(projectId, siblingSnapshotId, siblingFileId, now);
        insertSymbol(projectId, snapshotId, fileId, symbolId);
        insertSymbol(projectId, siblingSnapshotId, siblingFileId, siblingSymbolId);
        insertRelation(projectId, snapshotId, relationId, "demo/DeleteTarget", "demo/DeleteOther");
        insertSymbolChange(projectId, snapshotId, changeId, "demo/DeleteTarget");

        mockMvc.perform(delete("/api/projects/{projectId}/snapshots/{snapshotId}", projectId, snapshotId))
                .andExpect(status().isNoContent());

        assertEquals(1, countRows("project", "id", projectId));
        assertEquals(0, countRows("snapshot", "id", snapshotId));
        assertEquals(1, countRows("snapshot", "id", siblingSnapshotId));
        assertEquals(0, countRows("source_file", "snapshot_id", snapshotId));
        assertEquals(1, countRows("source_file", "snapshot_id", siblingSnapshotId));
        assertEquals(0, countRows("symbol", "snapshot_id", snapshotId));
        assertEquals(1, countRows("symbol", "snapshot_id", siblingSnapshotId));
        assertEquals(0, countRows("relation", "snapshot_id", snapshotId));
        assertEquals(0, countRows("symbol_change", "snapshot_id", snapshotId));
    }

    @Test
    void getSnapshotDiagnosticsReturnsPersistedIncrementalMetadata() throws Exception {
        String projectId = "project-snapshot-diagnostics";
        String snapshotId = "snapshot-diagnostics-1";
        String now = Instant.now().toString();

        insertProject(projectId, now);
        insertSnapshotWithDiagnostics(projectId, snapshotId, now);

        mockMvc.perform(get("/api/projects/{projectId}/snapshots/{snapshotId}/diagnostics", projectId, snapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(snapshotId))
                .andExpect(jsonPath("$.requestedMode").value("incremental"))
                .andExpect(jsonPath("$.effectiveMode").value("full"))
                .andExpect(jsonPath("$.changeSource").value("git"))
                .andExpect(jsonPath("$.includesWorkspaceChanges").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("Build configuration changed."))
                .andExpect(jsonPath("$.changedFiles[0]").value("pom.xml"))
                .andExpect(jsonPath("$.renamedPaths[0]").value("src/main/java/demo/Old.java -> src/main/java/demo/New.java"))
                .andExpect(jsonPath("$.rebuildPaths").isEmpty())
                .andExpect(jsonPath("$.removedPaths").isEmpty());
    }

    private void insertProject(String projectId, String now) {
        jdbcTemplate.update(
                """
                insert into project (id, name, root_path, build_tool, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                projectId,
                "snapshot-project",
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
                null,
                null,
                displayName,
                "completed",
                now
        );
    }

    private void insertSnapshotWithDiagnostics(String projectId, String snapshotId, String now) {
        jdbcTemplate.update(
                """
                insert into snapshot (
                    id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at,
                    requested_mode, effective_mode, change_source, includes_workspace_changes, diagnostics_note,
                    fallback_reason, changed_files_json, renamed_paths_json, rebuild_paths_json, removed_paths_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshotId,
                projectId,
                "snapshot-base-1",
                "git",
                null,
                null,
                "Incremental Review",
                "completed",
                now,
                "incremental",
                "full",
                "git",
                1,
                "Incremental fallback: build configuration changed, so a full scan was executed.",
                "Build configuration changed.",
                "[\"pom.xml\"]",
                "[\"src/main/java/demo/Old.java -> src/main/java/demo/New.java\"]",
                "[]",
                "[]"
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
                "src/main/java/demo/DeleteTarget.java",
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
                "demo/DeleteTarget",
                "type",
                null,
                "DeleteTarget",
                "DeleteTarget",
                "demo",
                "demo.DeleteTarget",
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

    private void insertRelation(String projectId, String snapshotId, String relationId, String sourceSymbolKey, String targetSymbolKey) {
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
                "uses_type",
                "high",
                null,
                null,
                null
        );
    }

    private void insertSymbolChange(String projectId, String snapshotId, String changeId, String symbolKey) {
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
                "added",
                "snapshot-delete-test"
        );
    }

    private int countRows(String tableName, String columnName, String value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                value
        );
    }
}
