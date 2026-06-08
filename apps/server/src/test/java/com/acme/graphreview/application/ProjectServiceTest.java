package com.acme.graphreview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.infrastructure.ProjectNotFoundException;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectServiceTest {

    private final InMemoryProjectRepository projectRepository = new InMemoryProjectRepository();
    private final ProjectService projectService = new ProjectService(projectRepository, new BuildToolDetector());

    @TempDir
    Path tempDir;

    @Test
    void importProjectCreatesProjectAndDetectsMaven() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project />");
        Path javaSourceRoot = Files.createDirectories(tempDir.resolve(Path.of("src", "main", "java", "demo")));
        Files.writeString(javaSourceRoot.resolve("DemoApp.java"), "class DemoApp {}");

        ProjectImportResult result = projectService.importProject(
                new ProjectImportCommand("demo", tempDir.toString())
        );

        assertTrue(result.created());
        assertEquals("demo", result.project().name());
        assertEquals("maven", result.project().buildTool());
        assertEquals(1, projectService.listProjects().size());
    }

    @Test
    void importProjectReusesExistingNormalizedRootPath() throws IOException {
        Files.createDirectories(tempDir.resolve("nested"));
        Path javaSourceRoot = Files.createDirectories(tempDir.resolve(Path.of("src", "main", "java", "demo")));
        Files.writeString(javaSourceRoot.resolve("DemoApp.java"), "class DemoApp {}");

        ProjectImportResult first = projectService.importProject(
                new ProjectImportCommand("demo", tempDir.toString())
        );
        ProjectImportResult second = projectService.importProject(
                new ProjectImportCommand("demo-again", tempDir.resolve("nested").resolve("..").toString())
        );

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.project().id(), second.project().id());
        assertEquals(1, projectService.listProjects().size());
    }

    @Test
    void importProjectRejectsMissingRootPath() {
        Path missingPath = tempDir.resolve("missing-project");

        ProjectValidationException exception = assertThrows(
                ProjectValidationException.class,
                () -> projectService.importProject(new ProjectImportCommand("demo", missingPath.toString()))
        );

        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void importProjectRejectsUnsupportedLanguageProjectWithoutSaving() throws IOException {
        Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"demo\"}");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src").resolve("index.ts"), "export const demo = true;");

        ProjectValidationException exception = assertThrows(
                ProjectValidationException.class,
                () -> projectService.importProject(new ProjectImportCommand("demo", tempDir.toString()))
        );

        assertEquals("Unsupported project language: only Java projects are supported.", exception.getMessage());
        assertTrue(projectService.listProjects().isEmpty());
    }

    @Test
    void importProjectAcceptsJavaSourceRootsWithoutBuildFiles() throws IOException {
        Path javaSourceRoot = Files.createDirectories(tempDir.resolve(Path.of("src", "main", "java", "demo")));
        Files.writeString(javaSourceRoot.resolve("DemoApp.java"), "class DemoApp {}");

        ProjectImportResult result = projectService.importProject(
                new ProjectImportCommand("demo", tempDir.toString())
        );

        assertTrue(result.created());
        assertEquals("unknown", result.project().buildTool());
        assertEquals(1, projectService.listProjects().size());
    }

    @Test
    void deleteProjectRemovesExistingProject() throws IOException {
        Path javaSourceRoot = Files.createDirectories(tempDir.resolve(Path.of("src", "main", "java", "demo")));
        Files.writeString(javaSourceRoot.resolve("DemoApp.java"), "class DemoApp {}");

        ProjectImportResult result = projectService.importProject(
                new ProjectImportCommand("demo", tempDir.toString())
        );

        projectService.deleteProject(result.project().id());

        assertTrue(projectService.listProjects().isEmpty());
        assertThrows(ProjectNotFoundException.class, () -> projectService.getProject(result.project().id()));
    }

    private static final class InMemoryProjectRepository implements ProjectRepository {

        private final List<RegisteredProject> projects = new ArrayList<>();

        @Override
        public Optional<RegisteredProject> findById(String id) {
            return projects.stream()
                    .filter(project -> project.id().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<RegisteredProject> findByRootPath(String rootPath) {
            return projects.stream()
                    .filter(project -> project.rootPath().equals(rootPath))
                    .findFirst();
        }

        @Override
        public List<RegisteredProject> findAll() {
            return projects.stream()
                    .sorted(Comparator.comparing(RegisteredProject::updatedAt).reversed())
                    .toList();
        }

        @Override
        public RegisteredProject save(RegisteredProject project) {
            Instant now = project.updatedAt();
            RegisteredProject storedProject = new RegisteredProject(
                    project.id(),
                    project.name(),
                    project.rootPath(),
                    project.buildTool(),
                    project.createdAt(),
                    now
            );
            projects.add(storedProject);
            return storedProject;
        }

        @Override
        public void deleteById(String id) {
            projects.removeIf(project -> project.id().equals(id));
        }
    }
}
