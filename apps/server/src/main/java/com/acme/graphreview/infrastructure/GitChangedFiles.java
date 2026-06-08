package com.acme.graphreview.infrastructure;

import java.util.List;

public record GitChangedFiles(
        boolean available,
        List<String> paths,
        String note
) {
    public static GitChangedFiles available(List<String> paths, String note) {
        return new GitChangedFiles(true, List.copyOf(paths), note);
    }

    public static GitChangedFiles unavailable(String note) {
        return new GitChangedFiles(false, List.of(), note);
    }
}
