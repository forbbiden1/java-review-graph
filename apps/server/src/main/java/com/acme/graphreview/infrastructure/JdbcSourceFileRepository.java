package com.acme.graphreview.infrastructure;

import com.acme.graphreview.application.SourceFileRepository;
import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.model.analysis.SourceFileRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSourceFileRepository implements SourceFileRepository {

    private static final RowMapper<StoredSourceFile> SOURCE_FILE_ROW_MAPPER = (resultSet, rowNum) -> new StoredSourceFile(
            resultSet.getString("id"),
            resultSet.getString("path"),
            resultSet.getString("module_name"),
            resultSet.getString("package_name"),
            resultSet.getString("content_hash"),
            resultSet.getString("scope")
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcSourceFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoredSourceFile> saveAll(String projectId, String snapshotId, List<SourceFileRecord> files) {
        List<StoredSourceFile> storedFiles = new ArrayList<>(files.size());
        for (SourceFileRecord file : files) {
            String id = UUID.randomUUID().toString();
            jdbcTemplate.update(
                    """
                    insert into source_file (id, project_id, snapshot_id, path, module_name, package_name, content_hash, scope, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    projectId,
                    snapshotId,
                    file.path(),
                    file.moduleName(),
                    file.packageName(),
                    file.contentHash(),
                    file.scope(),
                    Instant.now().toString()
            );
            storedFiles.add(new StoredSourceFile(
                    id,
                    file.path(),
                    file.moduleName(),
                    file.packageName(),
                    file.contentHash(),
                    file.scope()
            ));
        }
        return storedFiles;
    }

    @Override
    public List<StoredSourceFile> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
        return jdbcTemplate.query(
                """
                select id, path, module_name, package_name, content_hash, scope
                from source_file
                where project_id = ? and snapshot_id = ?
                order by path
                """,
                SOURCE_FILE_ROW_MAPPER,
                projectId,
                snapshotId
        );
    }
}
