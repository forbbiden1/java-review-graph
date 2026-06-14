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
        LinkedHashSet<String> renamedPaths = new LinkedHashSet<>();
        String headCommit = runGit(rootPath, List.of("git", "rev-parse", "HEAD")).orElse(null);
        boolean includesWorkspaceChanges = false;

        if (baseCommit != null && headCommit != null) {
            ParsedGitChanges committedChanges = runGitNameStatus(
                    rootPath,
                    List.of("git", "diff", "--name-status", "--find-renames", "-z", baseCommit, headCommit, "--")
            );
            changedPaths.addAll(committedChanges.paths());
            renamedPaths.addAll(committedChanges.renamedPaths());
        }

        if (headCommit != null) {
            ParsedGitChanges workspaceDiffChanges = runGitNameStatus(
                    rootPath,
                    List.of("git", "diff", "--name-status", "--find-renames", "-z", "HEAD", "--")
            );
            List<String> untrackedPaths = runGitNullSeparated(
                    rootPath,
                    List.of("git", "ls-files", "--others", "--exclude-standard", "-z")
            );
            includesWorkspaceChanges = !workspaceDiffChanges.paths().isEmpty() || !untrackedPaths.isEmpty();
            changedPaths.addAll(workspaceDiffChanges.paths());
            renamedPaths.addAll(workspaceDiffChanges.renamedPaths());
            changedPaths.addAll(untrackedPaths);
        } else {
            ParsedGitChanges statusChanges = readStatusPaths(rootPath);
            includesWorkspaceChanges = !statusChanges.paths().isEmpty();
            changedPaths.addAll(statusChanges.paths());
            renamedPaths.addAll(statusChanges.renamedPaths());
        }

        return GitChangedFiles.available(
                List.copyOf(changedPaths),
                List.copyOf(renamedPaths),
                buildChangedFilesNote(baseCommit, headCommit, changedPaths.size()),
                includesWorkspaceChanges
        );
    }

    public GitChangedFiles resolveCommitRangeChangedFiles(Path rootPath, String baseCommit, String targetCommit) {
        if (!isGitWorkTree(rootPath)) {
            return GitChangedFiles.unavailable(
                    "Commit-range Git change detection is unavailable because the project root is not a Git work tree."
            );
        }

        ParsedGitChanges commitRangeChanges = runGitNameStatus(
                rootPath,
                List.of("git", "diff", "--name-status", "--find-renames", "-z", baseCommit, targetCommit, "--")
        );

        return GitChangedFiles.available(
                commitRangeChanges.paths(),
                commitRangeChanges.renamedPaths(),
                buildCommitRangeChangedFilesNote(baseCommit, targetCommit, commitRangeChanges.paths().size()),
                false
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

    private ParsedGitChanges runGitNameStatus(Path rootPath, List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(rootPath.toFile())
                    .redirectErrorStream(true)
                    .start();

            boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                LOGGER.debug("Timed out while collecting git name-status for {}", rootPath);
                return ParsedGitChanges.empty();
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                LOGGER.debug("Git command failed in {}: {} -> {}", rootPath, String.join(" ", command), output.trim());
                return ParsedGitChanges.empty();
            }

            return parseNameStatusOutput(output);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.debug("Unable to collect git name-status for {}", rootPath, exception);
            return ParsedGitChanges.empty();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private ParsedGitChanges readStatusPaths(Path rootPath) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        LinkedHashSet<String> renamedPaths = new LinkedHashSet<>();
        runGit(rootPath, List.of("git", "status", "--porcelain"))
                .ifPresent(output -> output.lines()
                        .map(String::trim)
                        .filter(line -> line.length() >= 4)
                        .forEach(line -> {
                    String path = line.substring(3).trim().replace('\\', '/');
                    int renameSeparator = path.lastIndexOf(" -> ");
                    if (renameSeparator >= 0) {
                        String fromPath = path.substring(0, renameSeparator).trim().replace('\\', '/');
                        String toPath = path.substring(renameSeparator + 4).trim().replace('\\', '/');
                        if (!fromPath.isBlank()) {
                            paths.add(fromPath);
                        }
                        if (!toPath.isBlank()) {
                            paths.add(toPath);
                        }
                        if (!fromPath.isBlank() && !toPath.isBlank()) {
                            renamedPaths.add(fromPath + " -> " + toPath);
                        }
                        return;
                    }
                    if (!path.isBlank()) {
                        paths.add(path);
                    }
                        }));
        return new ParsedGitChanges(List.copyOf(paths), List.copyOf(renamedPaths));
    }

    private ParsedGitChanges parseNameStatusOutput(String output) {
        if (output.isEmpty()) {
            return ParsedGitChanges.empty();
        }

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        LinkedHashSet<String> renamedPaths = new LinkedHashSet<>();
        String[] tokens = output.split("\0");
        int index = 0;
        while (index < tokens.length) {
            String statusToken = tokens[index].trim();
            index += 1;
            if (statusToken.isBlank()) {
                continue;
            }

            char statusCode = statusToken.charAt(0);
            if (statusCode == 'R') {
                if (index + 1 >= tokens.length) {
                    break;
                }
                String fromPath = normalizeGitPath(tokens[index]);
                String toPath = normalizeGitPath(tokens[index + 1]);
                index += 2;
                if (!fromPath.isBlank()) {
                    paths.add(fromPath);
                }
                if (!toPath.isBlank()) {
                    paths.add(toPath);
                }
                if (!fromPath.isBlank() && !toPath.isBlank()) {
                    renamedPaths.add(fromPath + " -> " + toPath);
                }
                continue;
            }

            if (index >= tokens.length) {
                break;
            }
            String path = normalizeGitPath(tokens[index]);
            index += 1;
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return new ParsedGitChanges(List.copyOf(paths), List.copyOf(renamedPaths));
    }

    private String normalizeGitPath(String path) {
        return path == null ? "" : path.trim().replace('\\', '/');
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

    private String buildCommitRangeChangedFilesNote(String baseCommit, String targetCommit, int pathCount) {
        return "Commit-range Git diff collected " + pathCount
                + " changed path(s) from commit " + shortenCommit(baseCommit)
                + " to commit " + shortenCommit(targetCommit) + ".";
    }

    private String shortenCommit(String commit) {
        if (commit == null || commit.isBlank()) {
            return "unknown";
        }
        return commit.length() <= 8 ? commit : commit.substring(0, 8);
    }

    private record ParsedGitChanges(List<String> paths, List<String> renamedPaths) {
        private static ParsedGitChanges empty() {
            return new ParsedGitChanges(List.of(), List.of());
        }
    }
}
