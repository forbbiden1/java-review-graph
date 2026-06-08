package com.acme.graphreview.infrastructure;

import com.acme.graphreview.application.SymbolChangeRepository;
import com.acme.graphreview.domain.StoredSymbolChange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSymbolChangeRepository implements SymbolChangeRepository {

    private static final RowMapper<StoredSymbolChange> SYMBOL_CHANGE_ROW_MAPPER = new SymbolChangeRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcSymbolChangeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveAll(List<StoredSymbolChange> changes) {
        for (StoredSymbolChange change : changes) {
            jdbcTemplate.update(
                    """
                    insert into symbol_change (id, project_id, snapshot_id, symbol_key, before_symbol_id, after_symbol_id, change_type, reason)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    change.id(),
                    change.projectId(),
                    change.snapshotId(),
                    change.symbolKey(),
                    change.beforeSymbolId(),
                    change.afterSymbolId(),
                    change.changeType(),
                    change.reason()
            );
        }
    }

    @Override
    public List<StoredSymbolChange> findByProjectIdAndSnapshotId(String projectId, String snapshotId) {
        return jdbcTemplate.query(
                """
                select id, project_id, snapshot_id, symbol_key, before_symbol_id, after_symbol_id, change_type, reason
                from symbol_change
                where project_id = ? and snapshot_id = ?
                order by change_type, symbol_key
                """,
                SYMBOL_CHANGE_ROW_MAPPER,
                projectId,
                snapshotId
        );
    }

    private static final class SymbolChangeRowMapper implements RowMapper<StoredSymbolChange> {
        @Override
        public StoredSymbolChange mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new StoredSymbolChange(
                    resultSet.getString("id"),
                    resultSet.getString("project_id"),
                    resultSet.getString("snapshot_id"),
                    resultSet.getString("symbol_key"),
                    resultSet.getString("before_symbol_id"),
                    resultSet.getString("after_symbol_id"),
                    resultSet.getString("change_type"),
                    resultSet.getString("reason")
            );
        }
    }
}
