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

        if (!columnNames.contains("requested_mode")) {
            jdbcTemplate.execute("alter table snapshot add column requested_mode text not null default 'full'");
        }

        if (!columnNames.contains("effective_mode")) {
            jdbcTemplate.execute("alter table snapshot add column effective_mode text not null default 'full'");
        }

        if (!columnNames.contains("change_source")) {
            jdbcTemplate.execute("alter table snapshot add column change_source text");
        }

        if (!columnNames.contains("includes_workspace_changes")) {
            jdbcTemplate.execute("alter table snapshot add column includes_workspace_changes integer not null default 0");
        }

        if (!columnNames.contains("diagnostics_note")) {
            jdbcTemplate.execute("alter table snapshot add column diagnostics_note text");
        }

        if (!columnNames.contains("fallback_reason")) {
            jdbcTemplate.execute("alter table snapshot add column fallback_reason text");
        }

        if (!columnNames.contains("changed_files_json")) {
            jdbcTemplate.execute("alter table snapshot add column changed_files_json text not null default '[]'");
        }

        if (!columnNames.contains("renamed_paths_json")) {
            jdbcTemplate.execute("alter table snapshot add column renamed_paths_json text not null default '[]'");
        }

        if (!columnNames.contains("rebuild_paths_json")) {
            jdbcTemplate.execute("alter table snapshot add column rebuild_paths_json text not null default '[]'");
        }

        if (!columnNames.contains("removed_paths_json")) {
            jdbcTemplate.execute("alter table snapshot add column removed_paths_json text not null default '[]'");
        }

        jdbcTemplate.update(
                """
                update snapshot
                set display_name = id
                where display_name is null or trim(display_name) = ''
                """
        );

        jdbcTemplate.update(
                """
                update snapshot
                set requested_mode = 'full'
                where requested_mode is null or trim(requested_mode) = ''
                """
        );

        jdbcTemplate.update(
                """
                update snapshot
                set effective_mode = 'full'
                where effective_mode is null or trim(effective_mode) = ''
                """
        );

        jdbcTemplate.update(
                """
                update snapshot
                set changed_files_json = '[]'
                where changed_files_json is null or trim(changed_files_json) = ''
                """
        );

        jdbcTemplate.update(
                """
                update snapshot
                set rebuild_paths_json = '[]'
                where rebuild_paths_json is null or trim(rebuild_paths_json) = ''
                """
        );

        jdbcTemplate.update(
                """
                update snapshot
                set renamed_paths_json = '[]'
                where renamed_paths_json is null or trim(renamed_paths_json) = ''
                """
        );

        jdbcTemplate.update(
                """
                update snapshot
                set removed_paths_json = '[]'
                where removed_paths_json is null or trim(removed_paths_json) = ''
                """
        );
    }
}
