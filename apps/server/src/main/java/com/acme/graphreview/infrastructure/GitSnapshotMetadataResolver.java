package com.acme.graphreview.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitSnapshotMetadataResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitSnapshotMetadataResolver.class);
    private static final long PROCESS_TIMEOUT_SECONDS = 5;

    public GitSnapshotMetadata resolve(Path rootPath) {
        if (!isGitWorkTree(rootPath) || hasUncommittedChanges(rootPath)) {
            return GitSnapshotMetadata.uncommitted();
        }

        Optional<String> gitCommit = runGit(rootPath, List.of("git", "rev-parse", "HEAD"));
        if (gitCommit.isEmpty()) {
            return GitSnapshotMetadata.uncommitted();
        }

        Optional<String> gitCommitMessage = runGit(rootPath, List.of("git", "log", "-1", "--pretty=%s", "HEAD"));
        return new GitSnapshotMetadata(
                gitCommit.get(),
                gitCommitMessage.filter(message -> !message.isBlank()).orElse(null)
        );
    }

    public GitChangedFiles resolveChangedFiles(Path rootPath, String baseCommit) {
        if (!isGitWorkTree(rootPath)) {
            return GitChangedFiles.unavailable(
                    "Incremental Git change detection is unavailable because the project root is not a Git work tree."
            );
        }

        LinkedHashSet<String> changedPaths = new LinkedHashSet<>();
        String headCommit = runGit(rootPath, List.of("git", "rev-parse", "HEAD")).orElse(null);

        if (baseCommit != null && headCommit != null) {
            changedPaths.addAll(runGitNullSeparated(
                    rootPath,
                    List.of("git", "diff", "--name-only", "-z", baseCommit, headCommit, "--")
            ));
        }

        if (headCommit != null) {
            changedPaths.addAll(runGitNullSeparated(rootPath, List.of("git", "diff", "--name-only", "-z", "HEAD", "--")));
            changedPaths.addAll(runGitNullSeparated(
                    rootPath,
                    List.of("git", "ls-files", "--others", "--exclude-standard", "-z")
            ));
        } else {
            changedPaths.addAll(readStatusPaths(rootPath));
        }

        return GitChangedFiles.available(
                List.copyOf(changedPaths),
                buildChangedFilesNote(baseCommit, headCommit, changedPaths.size())
        );
    }

    private boolean isGitWorkTree(Path rootPath) {
        return runGit(rootPath, List.of("git", "rev-parse", "--is-inside-work-tree"))
                .map("true"::equalsIgnoreCase)
                .orElse(false);
    }

    private boolean hasUncommittedChanges(Path rootPath) {
        return runGit(rootPath, List.of("git", "status", "--porcelain"))
                .map(output -> !output.isBlank())
                .orElse(false);
    }

    private Optional<String> runGit(Path rootPath, List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(rootPath.toFile())
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                LOGGER.debug("Timed out while resolving git metadata for {}", rootPath);
                return Optional.empty();
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                LOGGER.debug("Git command failed in {}: {} -> {}", rootPath, String.join(" ", command), output);
                return Optional.empty();
            }
            return Optional.of(output);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.debug("Unable to resolve git metadata for {}", rootPath, exception);
            return Optional.empty();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private List<String> runGitNullSeparated(Path rootPath, List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(rootPath.toFile())
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                LOGGER.debug("Timed out while collecting git paths for {}", rootPath);
                return List.of();
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                LOGGER.debug("Git command failed in {}: {} -> {}", rootPath, String.join(" ", command), output.trim());
                return List.of();
            }

            return java.util.Arrays.stream(output.split("\0"))
                    .map(String::trim)
                    .filter(path -> !path.isBlank())
                    .map(path -> path.replace('\\', '/'))
                    .toList();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.debug("Unable to collect git paths for {}", rootPath, exception);
            return List.of();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private List<String> readStatusPaths(Path rootPath) {
        return runGit(rootPath, List.of("git", "status", "--porcelain"))
                .stream()
                .flatMap(output -> output.lines())
                .map(String::trim)
                .filter(line -> line.length() >= 4)
                .map(line -> {
                    String path = line.substring(3);
                    int renameSeparator = path.lastIndexOf(" -> ");
                    if (renameSeparator >= 0) {
                        path = path.substring(renameSeparator + 4);
                    }
                    return path.trim().replace('\\', '/');
                })
                .filter(path -> !path.isBlank())
                .toList();
    }

    private String buildChangedFilesNote(String baseCommit, String headCommit, int pathCount) {
        if (baseCommit != null && headCommit != null) {
            String baseShortCommit = shortenCommit(baseCommit);
            if (baseCommit.equals(headCommit)) {
                return "Incremental Git diff collected " + pathCount
                        + " changed path(s) from the current working tree based on commit " + baseShortCommit + ".";
            }
            return "Incremental Git diff collected " + pathCount
                    + " changed path(s) from commit " + baseShortCommit + " to the current workspace state.";
        }

        if (headCommit != null) {
            return "Incremental Git diff collected " + pathCount
                    + " changed path(s) from the current working tree because the latest snapshot has no committed Git base.";
        }

        return "Incremental Git status collected " + pathCount
                + " changed path(s) because the repository does not have a HEAD commit yet.";
    }

    private String shortenCommit(String commit) {
        if (commit == null || commit.isBlank()) {
            return "unknown";
        }
        return commit.length() <= 8 ? commit : commit.substring(0, 8);
    }
}
