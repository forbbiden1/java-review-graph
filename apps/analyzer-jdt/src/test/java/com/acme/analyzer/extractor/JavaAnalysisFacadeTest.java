package com.acme.analyzer.extractor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.acme.analyzer.parser.AnalysisRequest;
import com.acme.analyzer.project.ProjectDescriptor;
import com.acme.model.analysis.AnalysisSnapshot;
import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaAnalysisFacadeTest {

    @TempDir
    Path tempDir;

    @Test
    void analyzeExtractsUsesTypeRelationsForReferencedProjectTypes() throws IOException {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "com", "example"));
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("ReviewTarget.java"), """
                package com.example;

                class Dependency {
                }

                class ReviewTarget {
                    private final Dependency dependency;

                    ReviewTarget(Dependency dependency) {
                        this.dependency = dependency;
                        helper(new Dependency());
                    }

                    Dependency helper(Dependency input) {
                        Dependency local = input;
                        return local;
                    }
                }
                """);

        JavaAnalysisFacade facade = new JavaAnalysisFacade();
        AnalysisSnapshot snapshot = facade.analyze(
                new ProjectDescriptor(
                        "project-1",
                        "maven",
                        tempDir,
                        List.of(tempDir.resolve(Path.of("src", "main", "java"))),
                        List.of(tempDir)
                ),
                new AnalysisRequest("snapshot-1", false, List.of())
        );

        String reviewTargetKey = snapshot.symbols().stream()
                .filter(symbol -> symbol.symbolType() == SymbolType.TYPE)
                .filter(symbol -> symbol.qualifiedName().equals("com.example.ReviewTarget"))
                .map(SymbolRecord::symbolKey)
                .findFirst()
                .orElseThrow();
        String dependencyKey = snapshot.symbols().stream()
                .filter(symbol -> symbol.symbolType() == SymbolType.TYPE)
                .filter(symbol -> symbol.qualifiedName().equals("com.example.Dependency"))
                .map(SymbolRecord::symbolKey)
                .findFirst()
                .orElseThrow();

        assertTrue(
                snapshot.relations().stream()
                        .anyMatch(relation -> isUsesTypeRelation(relation, reviewTargetKey, dependencyKey)),
                "expected a uses_type edge from ReviewTarget to Dependency"
        );
    }

    private boolean isUsesTypeRelation(RelationRecord relation, String sourceKey, String targetKey) {
        return relation.relationType() == RelationType.USES_TYPE
                && relation.sourceSymbolKey().equals(sourceKey)
                && relation.targetSymbolKey().equals(targetKey);
    }

    @Test
    void incrementalRequestAnalyzesOnlyRequestedFiles() throws IOException {
        Path sourceRoot = tempDir.resolve(Path.of("src", "main", "java", "com", "example"));
        Files.createDirectories(sourceRoot);
        Files.writeString(sourceRoot.resolve("ChangedType.java"), """
                package com.example;

                class ChangedType {
                }
                """);
        Files.writeString(sourceRoot.resolve("UnchangedType.java"), """
                package com.example;

                class UnchangedType {
                }
                """);

        JavaAnalysisFacade facade = new JavaAnalysisFacade();
        AnalysisSnapshot snapshot = facade.analyze(
                new ProjectDescriptor(
                        "project-1",
                        "maven",
                        tempDir,
                        List.of(tempDir.resolve(Path.of("src", "main", "java"))),
                        List.of(tempDir)
                ),
                new AnalysisRequest("snapshot-2", true, List.of("src/main/java/com/example/ChangedType.java"))
        );

        assertTrue(snapshot.files().stream().anyMatch(file -> file.path().endsWith("ChangedType.java")));
        assertFalse(snapshot.files().stream().anyMatch(file -> file.path().endsWith("UnchangedType.java")));
    }
}
