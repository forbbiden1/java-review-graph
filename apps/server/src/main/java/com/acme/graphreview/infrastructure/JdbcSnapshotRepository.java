package com.acme.graphreview.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acme.graphreview.application.SnapshotRepository;
import com.acme.graphreview.domain.ProjectSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSnapshotRepository implements SnapshotRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final RowMapper<ProjectSnapshot> SNAPSHOT_ROW_MAPPER = new SnapshotRowMapper();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSnapshotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProjectSnapshot> findLatestByProjectId(String projectId) {
        return jdbcTemplate.query(
                """
                select id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at,
                       requested_mode, effective_mode, change_source, includes_workspace_changes, diagnostics_note,
                       fallback_reason, changed_files_json, renamed_paths_json, rebuild_paths_json, removed_paths_json
                from snapshot
                where project_id = ?
                order by created_at desc
                limit 1
                """,
                SNAPSHOT_ROW_MAPPER,
                projectId
        ).stream().findFirst();
    }

    @Override
    public Optional<ProjectSnapshot> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
        return jdbcTemplate.query(
                """
                select id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at,
                       requested_mode, effective_mode, change_source, includes_workspace_changes, diagnostics_note,
                       fallback_reason, changed_files_json, renamed_paths_json, rebuild_paths_json, removed_paths_json
                from snapshot
                where project_id = ? and id = ?
                limit 1
                """,
                SNAPSHOT_ROW_MAPPER,
                projectId,
                snapshotId
        ).stream().findFirst();
    }

    @Override
    public List<ProjectSnapshot> findByProjectId(String projectId) {
        return jdbcTemplate.query(
                """
                select id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at,
                       requested_mode, effective_mode, change_source, includes_workspace_changes, diagnostics_note,
                       fallback_reason, changed_files_json, renamed_paths_json, rebuild_paths_json, removed_paths_json
                from snapshot
                where project_id = ?
                order by created_at desc
                """,
                SNAPSHOT_ROW_MAPPER,
                projectId
        );
    }

    @Override
    public ProjectSnapshot save(ProjectSnapshot snapshot) {
        jdbcTemplate.update(
                """
                insert into snapshot (
                    id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at,
                    requested_mode, effective_mode, change_source, includes_workspace_changes, diagnostics_note,
                    fallback_reason, changed_files_json, renamed_paths_json, rebuild_paths_json, removed_paths_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.id(),
                snapshot.projectId(),
                snapshot.baseSnapshotId(),
                snapshot.triggerType(),
                snapshot.gitCommit(),
                snapshot.gitCommitMessage(),
                snapshot.displayName(),
                snapshot.status(),
                snapshot.createdAt().toString(),
                snapshot.requestedMode(),
                snapshot.effectiveMode(),
                snapshot.changeSource(),
                snapshot.includesWorkspaceChanges() ? 1 : 0,
                snapshot.diagnosticsNote(),
                snapshot.fallbackReason(),
                writeStringList(snapshot.changedFiles()),
                writeStringList(snapshot.renamedPaths()),
                writeStringList(snapshot.rebuildPaths()),
                writeStringList(snapshot.removedPaths())
        );
        return snapshot;
    }

    @Override
    public ProjectSnapshot rename(String projectId, String snapshotId, String displayName) {
        jdbcTemplate.update(
                """
                update snapshot
                set display_name = ?
                where project_id = ? and id = ?
                """,
                displayName,
                projectId,
                snapshotId
        );
        return findByProjectIdAndSnapshotId(projectId, snapshotId)
                .orElseThrow(() -> new SnapshotNotFoundException(projectId));
    }

    @Override
    @Transactional
    public void deleteByProjectIdAndSnapshotId(String projectId, String snapshotId) {
        jdbcTemplate.update(
                "update snapshot set base_snapshot_id = null where project_id = ? and base_snapshot_id = ?",
                projectId,
                snapshotId
        );
        jdbcTemplate.update("delete from symbol_change where project_id = ? and snapshot_id = ?", projectId, snapshotId);
        jdbcTemplate.update("delete from relation where project_id = ? and snapshot_id = ?", projectId, snapshotId);
        jdbcTemplate.update("delete from symbol where project_id = ? and snapshot_id = ?", projectId, snapshotId);
        jdbcTemplate.update("delete from source_file where project_id = ? and snapshot_id = ?", projectId, snapshotId);
        jdbcTemplate.update("delete from snapshot where project_id = ? and id = ?", projectId, snapshotId);
    }

    private static final class SnapshotRowMapper implements RowMapper<ProjectSnapshot> {
        @Override
        public ProjectSnapshot mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new ProjectSnapshot(
                    resultSet.getString("id"),
                    resultSet.getString("project_id"),
                    resultSet.getString("base_snapshot_id"),
                    resultSet.getString("trigger_type"),
                    resultSet.getString("git_commit"),
                    resultSet.getString("git_commit_message"),
                    resultSet.getString("display_name"),
                    resultSet.getString("status"),
                    Instant.parse(resultSet.getString("created_at")),
                    resultSet.getString("requested_mode"),
                    resultSet.getString("effective_mode"),
                    resultSet.getString("change_source"),
                    resultSet.getInt("includes_workspace_changes") != 0,
                    resultSet.getString("diagnostics_note"),
                    resultSet.getString("fallback_reason"),
                    readStringList(resultSet.getString("changed_files_json")),
                    readStringList(resultSet.getString("renamed_paths_json")),
                    readStringList(resultSet.getString("rebuild_paths_json")),
                    readStringList(resultSet.getString("removed_paths_json"))
            );
        }
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize snapshot diagnostics paths.", exception);
        }
    }

    private static List<String> readStringList(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(rawJson, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse snapshot diagnostics paths.", exception);
        }
    }
}
