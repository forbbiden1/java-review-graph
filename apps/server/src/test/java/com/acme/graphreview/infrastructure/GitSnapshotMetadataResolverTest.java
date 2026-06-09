package com.acme.graphreview.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSnapshotMetadataResolverTest {

    private final GitSnapshotMetadataResolver resolver = new GitSnapshotMetadataResolver();

    @TempDir
    Path tempDir;

    @Test
    void resolveReturnsHeadCommitForCleanRepository() throws IOException, InterruptedException {
        assumeGitAvailable();
        initializeRepository(tempDir);
        Files.writeString(tempDir.resolve("Demo.java"), "class Demo {}");
        runGit(tempDir, "git", "add", "Demo.java");
        runGit(tempDir, "git", "commit", "-m", "Initial review snapshot");

        GitSnapshotMetadata metadata = resolver.resolve(tempDir);

        assertEquals(readGit(tempDir, "git", "rev-parse", "HEAD"), metadata.gitCommit());
        assertEquals("Initial review snapshot", metadata.gitCommitMessage());
    }

    @Test
    void resolveReturnsUncommittedWhenWorkingTreeIsDirty() throws IOException, InterruptedException {
        assumeGitAvailable();
        initializeRepository(tempDir);
        Files.writeString(tempDir.resolve("Demo.java"), "class Demo {}");
        runGit(tempDir, "git", "add", "Demo.java");
        runGit(tempDir, "git", "commit", "-m", "Initial review snapshot");
        Files.writeString(tempDir.resolve("Demo.java"), "class Demo { int value = 1; }");

        GitSnapshotMetadata metadata = resolver.resolve(tempDir);

        assertNull(metadata.gitCommit());
        assertNull(metadata.gitCommitMessage());
    }

    @Test
    void resolveReturnsUncommittedForNonGitDirectory() throws IOException, InterruptedException {
        Path nonGitDir = Path.of(System.getProperty("user.home"))
                .resolve(".java-review-graph-non-git-" + UUID.randomUUID());
        Files.createDirectories(nonGitDir);
        try {
            Assumptions.assumeFalse(isInsideGitWorkTree(nonGitDir), "No non-git directory available in this environment");
            GitSnapshotMetadata metadata = resolver.resolve(nonGitDir);

            assertNull(metadata.gitCommit());
            assertNull(metadata.gitCommitMessage());
        } finally {
            Files.deleteIfExists(nonGitDir);
        }
    }

    @Test
    void resolveChangedFilesIncludesCommittedWorkspaceAndUntrackedChangesSinceBaseCommit() throws IOException, InterruptedException {
        assumeGitAvailable();
        initializeRepository(tempDir);

        Files.writeString(tempDir.resolve("Base.java"), "class Base {}");
        runGit(tempDir, "git", "add", "Base.java");
        runGit(tempDir, "git", "commit", "-m", "Base snapshot");
        String baseCommit = readGit(tempDir, "git", "rev-parse", "HEAD");

        Files.writeString(tempDir.resolve("Committed.java"), "class Committed {}");
        runGit(tempDir, "git", "add", "Committed.java");
        runGit(tempDir, "git", "commit", "-m", "Committed change");

        Files.writeString(tempDir.resolve("Base.java"), "class Base { int value = 1; }");
        Files.writeString(tempDir.resolve("Untracked.java"), "class Untracked {}");

        GitChangedFiles changedFiles = resolver.resolveChangedFiles(tempDir, baseCommit);

        assertTrue(changedFiles.available());
        assertEquals(List.of("Committed.java", "Base.java", "Untracked.java"), changedFiles.paths());
        assertTrue(changedFiles.note().contains("current workspace state"));
        assertTrue(changedFiles.note().contains(baseCommit.substring(0, 8)));
    }

    @Test
    void resolveChangedFilesFallsBackToGitStatusWhenRepositoryHasNoHeadCommit() throws IOException, InterruptedException {
        assumeGitAvailable();
        initializeRepository(tempDir);
        Files.writeString(tempDir.resolve("Draft.java"), "class Draft {}");

        GitChangedFiles changedFiles = resolver.resolveChangedFiles(tempDir, null);

        assertTrue(changedFiles.available());
        assertEquals(List.of("Draft.java"), changedFiles.paths());
        assertTrue(changedFiles.note().contains("does not have a HEAD commit yet"));
    }

    @Test
    void resolveChangedFilesDetectsRenamePathsExplicitly() throws IOException, InterruptedException {
        assumeGitAvailable();
        initializeRepository(tempDir);

        Files.writeString(tempDir.resolve("OldName.java"), "class OldName {}");
        runGit(tempDir, "git", "add", "OldName.java");
        runGit(tempDir, "git", "commit", "-m", "Base snapshot");
        String baseCommit = readGit(tempDir, "git", "rev-parse", "HEAD");

        runGit(tempDir, "git", "mv", "OldName.java", "NewName.java");

        GitChangedFiles changedFiles = resolver.resolveChangedFiles(tempDir, baseCommit);

        assertTrue(changedFiles.available());
        assertEquals(List.of("OldName.java", "NewName.java"), changedFiles.paths());
        assertEquals(List.of("OldName.java -> NewName.java"), changedFiles.renamedPaths());
    }

    @Test
    void resolveChangedFilesReturnsUnavailableForNonGitDirectory() throws IOException, InterruptedException {
        Path nonGitDir = Path.of(System.getProperty("user.home"))
                .resolve(".java-review-graph-non-git-" + UUID.randomUUID());
        Files.createDirectories(nonGitDir);
        try {
            Assumptions.assumeFalse(isInsideGitWorkTree(nonGitDir), "No non-git directory available in this environment");
            GitChangedFiles changedFiles = resolver.resolveChangedFiles(nonGitDir, null);

            assertFalse(changedFiles.available());
            assertTrue(changedFiles.paths().isEmpty());
            assertTrue(changedFiles.note().contains("not a Git work tree"));
        } finally {
            Files.deleteIfExists(nonGitDir);
        }
    }

    private void assumeGitAvailable() throws IOException, InterruptedException {
        Assumptions.assumeTrue(runProcess(tempDir, List.of("git", "--version")).exitCode() == 0, "git is unavailable");
    }

    private void initializeRepository(Path rootPath) throws IOException, InterruptedException {
        runGit(rootPath, "git", "init");
        runGit(rootPath, "git", "config", "user.name", "Test User");
        runGit(rootPath, "git", "config", "user.email", "test@example.com");
    }

    private void runGit(Path rootPath, String... command) throws IOException, InterruptedException {
        ProcessResult result = runProcess(rootPath, List.of(command));
        if (result.exitCode() != 0) {
            throw new IOException("Command failed: " + String.join(" ", command) + System.lineSeparator() + result.output());
        }
    }

    private String readGit(Path rootPath, String... command) throws IOException, InterruptedException {
        ProcessResult result = runProcess(rootPath, List.of(command));
        if (result.exitCode() != 0) {
            throw new IOException("Command failed: " + String.join(" ", command) + System.lineSeparator() + result.output());
        }
        return result.output();
    }

    private boolean isInsideGitWorkTree(Path rootPath) throws IOException, InterruptedException {
        ProcessResult result = runProcess(rootPath, List.of("git", "rev-parse", "--is-inside-work-tree"));
        return result.exitCode() == 0 && "true".equalsIgnoreCase(result.output());
    }

    private ProcessResult runProcess(Path rootPath, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(rootPath.toFile())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return new ProcessResult(exitCode, output);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
