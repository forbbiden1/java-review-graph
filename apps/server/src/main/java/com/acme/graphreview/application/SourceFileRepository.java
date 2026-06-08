package com.acme.graphreview.application;

import com.acme.graphreview.domain.StoredSourceFile;
import com.acme.model.analysis.SourceFileRecord;
import java.util.List;

public interface SourceFileRepository {

    List<StoredSourceFile> saveAll(String projectId, String snapshotId, List<SourceFileRecord> files);

    List<StoredSourceFile> findByProjectIdAndSnapshotId(String projectId, String snapshotId);
}
