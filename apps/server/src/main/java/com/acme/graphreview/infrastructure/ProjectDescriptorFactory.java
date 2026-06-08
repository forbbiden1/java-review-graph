package com.acme.graphreview.infrastructure;

import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.graphreview.domain.RegisteredProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class ProjectDescriptorFactory {

    public ProjectDescriptor create(RegisteredProject project) {
        Path rootPath = Path.of(project.rootPath());
        return new ProjectDescriptor(
                project.id(),
                project.buildTool(),
                rootPath,
                findSourceRoots(rootPath),
                findModuleRoots(rootPath)
        );
    }

    private List<Path> findSourceRoots(Path rootPath) {
        try (Stream<Path> paths = Files.find(rootPath, 8, (path, attributes) ->
                attributes.isDirectory() && isSourceRoot(rootPath, path))) {
            List<Path> sourceRoots = paths
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            return sourceRoots.isEmpty() ? List.of(rootPath) : sourceRoots;
        } catch (IOException exception) {
            throw new ProjectValidationException("Unable to scan source roots for project: " + rootPath, exception);
        }
    }

    private List<Path> findModuleRoots(Path rootPath) {
        try (Stream<Path> paths = Files.find(rootPath, 6, (path, attributes) ->
                attributes.isRegularFile() && isBuildFile(path.getFileName().toString()))) {
            List<Path> moduleRoots = paths
                    .map(Path::getParent)
                    .distinct()
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            return moduleRoots.isEmpty() ? List.of(rootPath) : moduleRoots;
        } catch (IOException exception) {
            throw new ProjectValidationException("Unable to scan module roots for project: " + rootPath, exception);
        }
    }

    private boolean isSourceRoot(Path rootPath, Path candidate) {
        Path relativePath = rootPath.relativize(candidate);
        return relativePath.endsWith(Path.of("src", "main", "java"))
                || relativePath.endsWith(Path.of("src", "test", "java"));
    }

    private boolean isBuildFile(String fileName) {
        return fileName.equals("pom.xml")
                || fileName.equals("build.gradle")
                || fileName.equals("build.gradle.kts")
                || fileName.equals("settings.gradle")
                || fileName.equals("settings.gradle.kts");
    }
}
