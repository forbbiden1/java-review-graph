package com.acme.graphreview.infrastructure;

import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SqliteSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SqliteSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        ensureSnapshotColumns();
    }

    private void ensureSnapshotColumns() {
        Set<String> columnNames = new HashSet<>(jdbcTemplate.query(
                "pragma table_info(snapshot)",
                (resultSet, rowNum) -> resultSet.getString("name")
        ));

        if (!columnNames.contains("git_commit_message")) {
            jdbcTemplate.execute("alter table snapshot add column git_commit_message text");
        }

        if (!columnNames.contains("display_name")) {
            jdbcTemplate.execute("alter table snapshot add column display_name text");
        }

        jdbcTemplate.update(
                """
                update snapshot
                set display_name = id
                where display_name is null or trim(display_name) = ''
                """
        );
    }
}
