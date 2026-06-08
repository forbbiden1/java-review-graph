package com.acme.graphreview.application;

import com.acme.model.graph.SymbolRecord;
import com.acme.model.graph.SymbolType;
import java.util.List;
import java.util.Map;

public interface SymbolRepository {

    void saveAll(String projectId, String snapshotId, List<SymbolRecord> symbols, Map<String, String> fileIdsByPath);

    List<SymbolRecord> findByProjectIdAndSnapshotId(String projectId, String snapshotId);

    List<SymbolRecord> findByProjectIdAndSnapshotIdAndType(String projectId, String snapshotId, SymbolType symbolType);

    List<SymbolRecord> findByProjectIdAndSnapshotIdAndParentSymbolKey(String projectId, String snapshotId, String parentSymbolKey);
}
