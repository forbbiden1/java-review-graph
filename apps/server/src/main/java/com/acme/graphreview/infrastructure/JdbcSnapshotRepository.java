package com.acme.graphreview.infrastructure;

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

    private static final RowMapper<ProjectSnapshot> SNAPSHOT_ROW_MAPPER = new SnapshotRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProjectSnapshot> findLatestByProjectId(String projectId) {
        return jdbcTemplate.query(
                """
                select id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at
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
                select id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at
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
                select id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at
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
                    id, project_id, base_snapshot_id, trigger_type, git_commit, git_commit_message, display_name, status, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.id(),
                snapshot.projectId(),
                snapshot.baseSnapshotId(),
                snapshot.triggerType(),
                snapshot.gitCommit(),
                snapshot.gitCommitMessage(),
                snapshot.displayName(),
                snapshot.status(),
                snapshot.createdAt().toString()
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
                    Instant.parse(resultSet.getString("created_at"))
            );
        }
    }
}
