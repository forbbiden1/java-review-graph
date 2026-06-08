package com.acme.graphreview.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class BuildToolDetector {

    public String detect(Path rootPath) {
        if (Files.exists(rootPath.resolve("pom.xml"))) {
            return "maven";
        }
        if (Files.exists(rootPath.resolve("build.gradle"))
                || Files.exists(rootPath.resolve("build.gradle.kts"))
                || Files.exists(rootPath.resolve("settings.gradle"))
                || Files.exists(rootPath.resolve("settings.gradle.kts"))) {
            return "gradle";
        }
        return "unknown";
    }
}
