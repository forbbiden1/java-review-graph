package com.acme.graphreview.infrastructure;

import java.util.List;

public record GitChangedFiles(
        boolean available,
        List<String> paths,
        List<String> renamedPaths,
        String note,
        boolean includesWorkspaceChanges
) {
    public static GitChangedFiles available(List<String> paths, String note) {
        return new GitChangedFiles(true, List.copyOf(paths), List.of(), note, false);
    }

    public static GitChangedFiles available(List<String> paths, String note, boolean includesWorkspaceChanges) {
        return new GitChangedFiles(true, List.copyOf(paths), List.of(), note, includesWorkspaceChanges);
    }

    public static GitChangedFiles available(
            List<String> paths,
            List<String> renamedPaths,
            String note,
            boolean includesWorkspaceChanges
    ) {
        return new GitChangedFiles(
                true,
                List.copyOf(paths),
                renamedPaths == null ? List.of() : List.copyOf(renamedPaths),
                note,
                includesWorkspaceChanges
        );
    }

    public static GitChangedFiles unavailable(String note) {
        return new GitChangedFiles(false, List.of(), List.of(), note, false);
    }
}
