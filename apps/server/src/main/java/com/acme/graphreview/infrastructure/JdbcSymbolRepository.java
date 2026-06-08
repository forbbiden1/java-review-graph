package com.acme.graphreview.infrastructure;

import com.acme.graphreview.application.SymbolRepository;
import com.acme.model.graph.SymbolKind;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import com.acme.model.review.ChangeStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSymbolRepository implements SymbolRepository {

    private static final RowMapper<SymbolRecord> SYMBOL_ROW_MAPPER = new SymbolRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcSymbolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(String projectId, String snapshotId, List<SymbolRecord> symbols, Map<String, String> fileIdsByPath) {
        for (SymbolRecord symbol : symbols) {
            jdbcTemplate.update(
                    """
                    insert into symbol (
                      id, project_id, snapshot_id, file_id, symbol_key, symbol_type, parent_symbol_key, name, display_name,
                      package_name, qualified_name, signature, kind, visibility, is_abstract, is_static, start_line, end_line,
                      api_hash, impl_hash, change_status, metadata_json
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID().toString(),
                    projectId,
                    snapshotId,
                    fileIdsByPath.get(symbol.filePath()),
                    symbol.symbolKey(),
                    symbol.symbolType().name().toLowerCase(),
                    symbol.parentSymbolKey(),
                    symbol.name(),
                    symbol.displayName(),
                    symbol.packageName(),
                    symbol.qualifiedName(),
                    symbol.signature(),
                    symbol.kind().name().toLowerCase(),
                    null,
                    false,
                    false,
                    symbol.startLine(),
                    symbol.endLine(),
                    symbol.apiHash(),
                    symbol.implHash(),
                    symbol.changeStatus().name().toLowerCase(),
                    null
            );
        }
    }

    @Override
    public List<SymbolRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
        return jdbcTemplate.query(
                """
                select symbol.symbol_key,
                       symbol.symbol_type,
                       symbol.kind,
                       symbol.parent_symbol_key,
                       symbol.name,
                       symbol.package_name as package_name,
                       symbol.qualified_name as qualified_name,
                       symbol.display_name,
                       symbol.signature,
                       source_file.path as file_path,
                       symbol.start_line,
                       symbol.end_line,
                       symbol.api_hash,
                       symbol.impl_hash,
                       symbol.change_status
                from symbol
                join source_file on source_file.id = symbol.file_id
                where symbol.project_id = ? and symbol.snapshot_id = ?
                order by symbol.symbol_type, symbol.qualified_name, symbol.start_line
                """,
                SYMBOL_ROW_MAPPER,
                projectId,
                snapshotId
        );
    }

    @Override
    public List<SymbolRecord> findByProjectIdAndSnapshotIdAndType(String projectId, String snapshotId, SymbolType symbolType) {
        return jdbcTemplate.query(
                """
                select symbol.symbol_key,
                       symbol.symbol_type,
                       symbol.kind,
                       symbol.parent_symbol_key,
                       symbol.name,
                       symbol.package_name as package_name,
                       symbol.qualified_name as qualified_name,
                       symbol.display_name,
                       symbol.signature,
                       source_file.path as file_path,
                       symbol.start_line,
                       symbol.end_line,
                       symbol.api_hash,
                       symbol.impl_hash,
                       symbol.change_status
                from symbol
                join source_file on source_file.id = symbol.file_id
                where symbol.project_id = ? and symbol.snapshot_id = ? and symbol.symbol_type = ?
                order by symbol.qualified_name, symbol.start_line
                """,
                SYMBOL_ROW_MAPPER,
                projectId,
                snapshotId,
                symbolType.name().toLowerCase()
        );
    }

    @Override
    public List<SymbolRecord> findByProjectIdAndSnapshotIdAndParentSymbolKey(String projectId, String snapshotId, String parentSymbolKey) {
        return jdbcTemplate.query(
                """
                select symbol.symbol_key,
                       symbol.symbol_type,
                       symbol.kind,
                       symbol.parent_symbol_key,
                       symbol.name,
                       symbol.package_name as package_name,
                       symbol.qualified_name as qualified_name,
                       symbol.display_name,
                       symbol.signature,
                       source_file.path as file_path,
                       symbol.start_line,
                       symbol.end_line,
                       symbol.api_hash,
                       symbol.impl_hash,
                       symbol.change_status
                from symbol
                join source_file on source_file.id = symbol.file_id
                where symbol.project_id = ? and symbol.snapshot_id = ? and symbol.parent_symbol_key = ?
                order by symbol.start_line
                """,
                SYMBOL_ROW_MAPPER,
                projectId,
                snapshotId,
                parentSymbolKey
        );
    }

    private static final class SymbolRowMapper implements RowMapper<SymbolRecord> {
        @Override
        public SymbolRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new SymbolRecord(
                    resultSet.getString("symbol_key"),
                    SymbolType.valueOf(resultSet.getString("symbol_type").toUpperCase()),
                    SymbolKind.valueOf(resultSet.getString("kind").toUpperCase()),
                    resultSet.getString("parent_symbol_key"),
                    resultSet.getString("name"),
                    resultSet.getString("package_name"),
                    resultSet.getString("qualified_name"),
                    resultSet.getString("display_name"),
                    resultSet.getString("signature"),
                    resultSet.getString("file_path"),
                    resultSet.getInt("start_line"),
                    resultSet.getInt("end_line"),
                    resultSet.getString("api_hash"),
                    resultSet.getString("impl_hash"),
                    ChangeStatus.valueOf(resultSet.getString("change_status").toUpperCase())
            );
        }
    }
}
