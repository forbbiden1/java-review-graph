package com.acme.graphreview.application;

import com.acme.graphreview.domain.ProjectSnapshot;
import java.util.List;
import java.util.Optional;

public interface SnapshotRepository {

    Optional<ProjectSnapshot> findLatestByProjectId(String projectId);

    Optional<ProjectSnapshot> findByProjectIdAndSnapshotId(String projectId, String snapshotId);

    List<ProjectSnapshot> findByProjectId(String projectId);

    ProjectSnapshot save(ProjectSnapshot snapshot);

    ProjectSnapshot rename(String projectId, String snapshotId, String displayName);

    void deleteByProjectIdAndSnapshotId(String projectId, String snapshotId);
}
