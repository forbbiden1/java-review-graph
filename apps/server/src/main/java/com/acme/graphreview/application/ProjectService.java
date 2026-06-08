package com.acme.graphreview.application;

import com.acme.graphreview.domain.RegisteredProject;
import com.acme.graphreview.infrastructure.ProjectNotFoundException;
import com.acme.graphreview.infrastructure.ProjectValidationException;
import com.acme.graphreview.infrastructure.UnsupportedProjectLanguageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final int JAVA_FILE_SCAN_DEPTH = 10;
    private static final int SOURCE_ROOT_SCAN_DEPTH = 8;

    private final ProjectRepository projectRepository;
    private final BuildToolDetector buildToolDetector;

    public ProjectService(ProjectRepository projectRepository, BuildToolDetector buildToolDetector) {
        this.projectRepository = projectRepository;
        this.buildToolDetector = buildToolDetector;
    }

    public ProjectImportResult importProject(ProjectImportCommand command) {
        Path normalizedRootPath = normalizeAndValidateRootPath(command.rootPath());
        validateSupportedProject(normalizedRootPath);
        String normalizedPathString = normalizedRootPath.toString();

        return projectRepository.findByRootPath(normalizedPathString)
                .map(existing -> new ProjectImportResult(existing, false))
                .orElseGet(() -> createProject(command.name(), normalizedRootPath));
    }

    public List<RegisteredProject> listProjects() {
        return projectRepository.findAll();
    }

    public RegisteredProject getProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    @Transactional
    public void deleteProject(String projectId) {
        getProject(projectId);
        projectRepository.deleteById(projectId);
    }

    private ProjectImportResult createProject(String name, Path normalizedRootPath) {
        Instant now = Instant.now();
        RegisteredProject project = new RegisteredProject(
                UUID.randomUUID().toString(),
                name.trim(),
                normalizedRootPath.toString(),
                buildToolDetector.detect(normalizedRootPath),
                now,
                now
        );
        return new ProjectImportResult(projectRepository.save(project), true);
    }

    private Path normalizeAndValidateRootPath(String rawRootPath) {
        if (rawRootPath == null || rawRootPath.isBlank()) {
            throw new ProjectValidationException("Project root path must not be blank.");
        }
        Path path;
        try {
            path = Path.of(rawRootPath.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new ProjectValidationException("Project root path is invalid: " + rawRootPath, exception);
        }
        if (!Files.exists(path)) {
            throw new ProjectValidationException("Project root path does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new ProjectValidationException("Project root path is not a directory: " + path);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path;
        }
    }

    private void validateSupportedProject(Path rootPath) {
        if (hasJavaSourceRoot(rootPath) || hasJavaSourceFile(rootPath)) {
            return;
        }
        throw new UnsupportedProjectLanguageException("Unsupported project language: only Java projects are supported.");
    }

    private boolean hasJavaSourceRoot(Path rootPath) {
        try (Stream<Path> paths = Files.find(rootPath, SOURCE_ROOT_SCAN_DEPTH, (path, attributes) ->
                attributes.isDirectory() && isJavaSourceRoot(rootPath, path))) {
            return paths.findFirst().isPresent();
        } catch (IOException exception) {
            throw new ProjectValidationException("Unable to inspect project language for: " + rootPath, exception);
        }
    }

    private boolean hasJavaSourceFile(Path rootPath) {
        try (Stream<Path> paths = Files.find(rootPath, JAVA_FILE_SCAN_DEPTH, (path, attributes) ->
                attributes.isRegularFile() && path.getFileName().toString().endsWith(".java"))) {
            return paths.findFirst().isPresent();
        } catch (IOException exception) {
            throw new ProjectValidationException("Unable to inspect project language for: " + rootPath, exception);
        }
    }

    private boolean isJavaSourceRoot(Path rootPath, Path candidate) {
        Path relativePath = rootPath.relativize(candidate);
        return relativePath.endsWith(Path.of("src", "main", "java"))
                || relativePath.endsWith(Path.of("src", "test", "java"));
    }
}
