package com.acme.graphreview.infrastructure;

import java.util.List;

public record GitChangedFiles(
        boolean available,
        List<String> paths,
        String note,
        boolean includesWorkspaceChanges
) {
    public static GitChangedFiles available(List<String> paths, String note) {
        return new GitChangedFiles(true, List.copyOf(paths), note, false);
    }

    public static GitChangedFiles available(List<String> paths, String note, boolean includesWorkspaceChanges) {
        return new GitChangedFiles(true, List.copyOf(paths), note, includesWorkspaceChanges);
    }

    public static GitChangedFiles unavailable(String note) {
        return new GitChangedFiles(false, List.of(), note, false);
    }
}
