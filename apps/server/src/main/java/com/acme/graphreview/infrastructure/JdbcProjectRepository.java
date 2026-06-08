package com.acme.graphreview.infrastructure;

import com.acme.graphreview.application.ProjectRepository;
import com.acme.graphreview.domain.RegisteredProject;
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
public class JdbcProjectRepository implements ProjectRepository {

    private static final RowMapper<RegisteredProject> PROJECT_ROW_MAPPER = new ProjectRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RegisteredProject> findById(String id) {
        return jdbcTemplate.query(
                "select id, name, root_path, build_tool, created_at, updated_at from project where id = ?",
                PROJECT_ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public Optional<RegisteredProject> findByRootPath(String rootPath) {
        return jdbcTemplate.query(
                "select id, name, root_path, build_tool, created_at, updated_at from project where root_path = ?",
                PROJECT_ROW_MAPPER,
                rootPath
        ).stream().findFirst();
    }

    @Override
    public List<RegisteredProject> findAll() {
        return jdbcTemplate.query(
                "select id, name, root_path, build_tool, created_at, updated_at from project order by updated_at desc",
                PROJECT_ROW_MAPPER
        );
    }

    @Override
    public RegisteredProject save(RegisteredProject project) {
        jdbcTemplate.update(
                """
                insert into project (id, name, root_path, build_tool, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                project.id(),
                project.name(),
                project.rootPath(),
                project.buildTool(),
                project.createdAt().toString(),
                project.updatedAt().toString()
        );
        return project;
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        jdbcTemplate.update("delete from symbol_change where project_id = ?", id);
        jdbcTemplate.update("delete from relation where project_id = ?", id);
        jdbcTemplate.update("delete from symbol where project_id = ?", id);
        jdbcTemplate.update("delete from source_file where project_id = ?", id);
        jdbcTemplate.update("delete from snapshot where project_id = ?", id);
        jdbcTemplate.update("delete from project where id = ?", id);
    }

    private static final class ProjectRowMapper implements RowMapper<RegisteredProject> {
        @Override
        public RegisteredProject mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new RegisteredProject(
                    resultSet.getString("id"),
                    resultSet.getString("name"),
                    resultSet.getString("root_path"),
                    resultSet.getString("build_tool"),
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at"))
            );
        }
    }
}
