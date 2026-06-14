package com.acme.graphreview.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:review-graph-index-test?mode=memory&cache=shared")
class SqliteSchemaIndexesIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void schemaInitializesQueryIndexesForHotPaths() {
        Set<String> indexNames = jdbcTemplate.query(
                "select name from sqlite_master where type = 'index'",
                (resultSet, rowNum) -> resultSet.getString("name")
        ).stream().collect(Collectors.toSet());

        assertTrue(indexNames.contains("idx_snapshot_project_created_at"));
        assertTrue(indexNames.contains("idx_snapshot_project_base_snapshot"));
        assertTrue(indexNames.contains("idx_source_file_project_snapshot_path"));
        assertTrue(indexNames.contains("idx_symbol_project_snapshot_key"));
        assertTrue(indexNames.contains("idx_symbol_project_snapshot_parent"));
        assertTrue(indexNames.contains("idx_relation_project_snapshot_type"));
        assertTrue(indexNames.contains("idx_relation_project_snapshot_type_source_target"));
    }
}
