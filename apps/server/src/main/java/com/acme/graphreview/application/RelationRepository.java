package com.acme.graphreview.application;

import com.acme.model.graph.RelationRecord;
import com.acme.model.graph.RelationType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RelationRepository {

    void saveAll(String projectId, String snapshotId, List<RelationRecord> relations, Map<String, String> fileIdsByPath);

    List<RelationRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId);

    List<RelationRecord> findByProjectIdAndSnapshotIdAndTypes(
            String projectId,
            String snapshotId,
            List<RelationType> relationTypes
    );

    List<RelationRecord> findByProjectIdAndSnapshotIdAndSymbolKeys(
            String projectId,
            String snapshotId,
            RelationType relationType,
            Set<String> sourceOrTargetSymbolKeys
    );
}
