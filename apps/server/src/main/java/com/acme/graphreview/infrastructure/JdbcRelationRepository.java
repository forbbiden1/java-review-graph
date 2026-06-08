package com.acme.graphreview.infrastructure;

import com.acme.graphreview.application.RelationRepository;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRelationRepository implements RelationRepository {

    private static final RowMapper<RelationRecord> RELATION_ROW_MAPPER = new RelationRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcRelationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(String projectId, String snapshotId, List<RelationRecord> relations, Map<String, String> fileIdsByPath) {
        for (RelationRecord relation : relations) {
            jdbcTemplate.update(
                    """
                    insert into relation (
                      id, project_id, snapshot_id, source_symbol_key, target_symbol_key, relation_type, confidence,
                      source_file_id, source_line, metadata_json
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID().toString(),
                    projectId,
                    snapshotId,
                    relation.sourceSymbolKey(),
                    relation.targetSymbolKey(),
                    relation.relationType().name().toLowerCase(),
                    relation.confidence(),
                    relation.filePath() == null ? null : fileIdsByPath.get(relation.filePath()),
                    relation.sourceLine(),
                    null
            );
        }
    }

    @Override
    public List<RelationRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
        return jdbcTemplate.query(
                """
                select relation.source_symbol_key,
                       relation.target_symbol_key,
                       relation.relation_type,
                       relation.confidence,
                       source_file.path as file_path,
                       relation.source_line
                from relation
                left join source_file on source_file.id = relation.source_file_id
                where project_id = ? and snapshot_id = ?
                order by relation_type, source_symbol_key, target_symbol_key
                """,
                RELATION_ROW_MAPPER,
                projectId,
                snapshotId
        );
    }

    private static final class RelationRowMapper implements RowMapper<RelationRecord> {
        @Override
        public RelationRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new RelationRecord(
                    resultSet.getString("source_symbol_key"),
                    resultSet.getString("target_symbol_key"),
                    RelationType.valueOf(resultSet.getString("relation_type").toUpperCase()),
                    resultSet.getString("confidence"),
                    resultSet.getString("file_path"),
                    resultSet.getObject("source_line", Integer.class)
            );
        }
    }
}
