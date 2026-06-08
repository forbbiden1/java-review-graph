package com.acme.graphreview.application;

import com.acme.model.graph.RelationRecord;
import java.util.List;
import java.util.Map;

public interface RelationRepository {

    void saveAll(String projectId, String snapshotId, List<RelationRecord> relations, Map<String, String> fileIdsByPath);

    List<RelationRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId);
}
